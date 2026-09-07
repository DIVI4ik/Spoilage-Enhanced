package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a slice of a spoiled cake do what a spoiled item does.
 *
 * <p>A cake is eaten as a BLOCK, not as an item — nothing ever passes through
 * {@code ItemStack.finishUsingItem}, which is where every other rotten effect lives. So a rotten
 * cake put on the ground fed the player like a fresh one, and that made placing it a way to
 * launder spoilage away entirely.</p>
 *
 * <p>The previous answer was to refuse the placement. That shut the hole in the wrong place: a
 * player could no longer set a spoiled cake down at all, and spoiled food is supposed to be bad
 * to eat, not impossible to handle. The effects belong on the eating path, which is here.</p>
 *
 * <p>The condition comes from the block's own tracked state, written when the cake was placed
 * (see {@code GourdBlockMixin}), so a cake that spoiled while standing on the table counts too —
 * not only one that was already rotten when it was put down.</p>
 *
 * <p>Split across HEAD and RETURN on purpose. The state has to be read while the block is still
 * standing, because the seventh slice removes it; the effects have to wait for the return value,
 * because vanilla answers PASS and eats nothing when the player is already full, and a refused
 * bite must not poison anybody.</p>
 */
@Mixin(CakeBlock.class)
public abstract class CakeEatMixin {

    @Unique
    private static final ThreadLocal<FoodSpoilageUtil.SpoilageState> spoilage_enhanced$biteState =
            new ThreadLocal<>();

    /** Nutrition vanilla grants for one slice — {@code player.getFoodData().eat(2, 0.1F)}. */
    @Unique
    private static final int spoilage_enhanced$SLICE_NUTRITION = 2;

    @Unique
    private static final float spoilage_enhanced$SLICE_SATURATION = 0.1F;

    @Inject(method = "eat", at = @At("HEAD"))
    private static void spoilage_enhanced$readConditionBeforeBite(LevelAccessor level, BlockPos pos,
            BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        spoilage_enhanced$biteState.remove();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockSpoilageData data = BlockSpoilageData.get(serverLevel);
        // Only ask about a cake that is already tracked. getSpoilageState registers an untracked
        // block as a side effect of being asked, and eating from a cake nobody was tracking must
        // not be what starts a clock on it.
        if (!data.isTracked(pos)) {
            return;
        }
        spoilage_enhanced$biteState.set(data.getSpoilageState(pos, serverLevel, Items.CAKE));
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private static void spoilage_enhanced$applyConditionAfterBite(LevelAccessor level, BlockPos pos,
            BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        FoodSpoilageUtil.SpoilageState condition = spoilage_enhanced$biteState.get();
        spoilage_enhanced$biteState.remove();

        if (condition == null || player == null) {
            return;
        }
        // A full player gets PASS and eats nothing. No bite, no consequences.
        InteractionResult result = cir.getReturnValue();
        if (result == null || !result.consumesAction()) {
            return;
        }

        SpoilageConfig.EffectsConfig fx = SpoilageConfig.getInstance().getEffectsConfig();
        switch (condition) {
            case FRESH -> {
                // Nothing to do — vanilla already fed the player.
            }
            case STALE -> {
                int penalty = (int) (spoilage_enhanced$SLICE_NUTRITION
                        * fx.stale_hunger_penalty_percent / 100.0);
                float satPenalty = (float) (spoilage_enhanced$SLICE_SATURATION
                        * fx.stale_saturation_penalty_percent / 100.0);
                player.getFoodData().setFoodLevel(
                        Math.max(0, player.getFoodData().getFoodLevel() - penalty));
                player.getFoodData().setSaturation(
                        Math.max(0f, player.getFoodData().getSaturationLevel() - satPenalty));
                if (level.getRandom().nextFloat() < (float) fx.stale_nausea_chance) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.NAUSEA, fx.stale_nausea_duration_ticks, 0));
                }
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS,
                        "Player ate a slice of STALE cake at " + pos);
            }
            case ROTTEN -> {
                if (fx.rotten_removes_all_hunger) {
                    player.getFoodData().setFoodLevel(Math.max(0,
                            player.getFoodData().getFoodLevel() - spoilage_enhanced$SLICE_NUTRITION));
                    player.getFoodData().setSaturation(Math.max(0f,
                            player.getFoodData().getSaturationLevel() - spoilage_enhanced$SLICE_SATURATION));
                }
                player.addEffect(new MobEffectInstance(
                        MobEffects.POISON, fx.rotten_poison_duration_ticks, 0));
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS,
                        "Player ate a slice of ROTTEN cake at " + pos + " -> Poison applied.");
            }
        }
    }
}
