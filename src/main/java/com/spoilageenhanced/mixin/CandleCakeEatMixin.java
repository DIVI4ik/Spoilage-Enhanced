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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a slice of a spoiled candle cake do what a spoiled item does.
 *
 * <p>A candle cake is eaten as a BLOCK via {@code CandleCakeBlock.useWithoutItem},
 * which calls {@code CakeBlock.eat} with {@code Blocks.CAKE.defaultBlockState()}.
 * The {@link CakeEatMixin} injects into {@code CakeBlock.eat}, but that method
 * receives {@code Blocks.CAKE.defaultBlockState()} as the state, so it reads the
 * spoilage state for {@code Items.CAKE} instead of the candle cake's own item
 * ({@code Items.CANDLE_CAKE}). This mixin intercepts the call at the source:
 * {@code CandleCakeBlock.useWithoutItem}, reads the correct state for the
 * candle cake's own item, and applies the effects on return.</p>
 */
@Mixin(CandleCakeBlock.class)
public abstract class CandleCakeEatMixin {

    @Unique
    private static final ThreadLocal<FoodSpoilageUtil.SpoilageState> spoilage_enhanced$biteState =
            new ThreadLocal<>();

    /** Nutrition vanilla grants for one slice — {@code player.getFoodData().eat(2, 0.1F)}. */
    @Unique
    private static final int spoilage_enhanced$SLICE_NUTRITION = 2;

    @Unique
    private static final float spoilage_enhanced$SLICE_SATURATION = 0.1F;

    @Inject(method = "useWithoutItem", at = @At("HEAD"))
    private static void spoilage_enhanced$readConditionBeforeBite(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockSpoilageData data = BlockSpoilageData.get(serverLevel);
        // Only ask about a candle cake that is already tracked.
        if (!data.isTracked(pos)) {
            return;
        }
        // CandleCakeBlock.useWithoutItem calls CakeBlock.eat with Blocks.CAKE.defaultBlockState(),
        // so we must use the block's own item to get the right duration.
        Item cakeItem = state.getBlock().asItem();
        spoilage_enhanced$biteState.set(data.getSpoilageState(pos, serverLevel, cakeItem));
    }

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private static void spoilage_enhanced$applyConditionAfterBite(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        FoodSpoilageUtil.SpoilageState condition = spoilage_enhanced$biteState.get();
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
                        "Player ate a slice of STALE candle cake at " + pos);
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
                        "Player ate a slice of ROTTEN candle cake at " + pos + " -> Poison applied.");
            }
        }
    }
}