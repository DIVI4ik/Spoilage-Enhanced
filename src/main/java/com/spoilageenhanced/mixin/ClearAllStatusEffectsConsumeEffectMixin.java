package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public class ClearAllStatusEffectsConsumeEffectMixin {

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void onClearAllEffects(Level world, ItemStack stack, LivingEntity user, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClientSide() && stack != null && !stack.isEmpty()
                && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
            FoodSpoilageUtil.SpoilageState state = FoodSpoilageUtil.getWorstState(stack);
            SpoilageConfig.MilkEffectsConfig milkCfg = SpoilageConfig.getInstance().getMilkEffectsConfig();
            if (state == FoodSpoilageUtil.SpoilageState.STALE) {
                if (milkCfg.stale_nausea_duration_ticks > 0) {
                    user.addEffect(new MobEffectInstance(MobEffects.NAUSEA, milkCfg.stale_nausea_duration_ticks, 0));
                }
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "Consumed Stale Milk -> Nausea applied.");
            } else if (state == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                // Cancel effect clearing for rotten milk!
                if (milkCfg.rotten_nausea_duration_ticks > 0) {
                    user.addEffect(new MobEffectInstance(MobEffects.NAUSEA, milkCfg.rotten_nausea_duration_ticks, 0));
                }
                if (milkCfg.rotten_poison_duration_ticks > 0) {
                    user.addEffect(new MobEffectInstance(MobEffects.POISON, milkCfg.rotten_poison_duration_ticks, 0));
                }
                if (milkCfg.rotten_hunger_duration_ticks > 0) {
                    user.addEffect(new MobEffectInstance(MobEffects.HUNGER, milkCfg.rotten_hunger_duration_ticks, 0));
                }
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "Consumed Rotten Milk -> Cancelled clear, applied Poison & Nausea & Hunger.");
                if (milkCfg.rotten_blocks_effect_clearing) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
