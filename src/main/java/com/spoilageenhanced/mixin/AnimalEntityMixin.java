package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalEntityMixin {

    @Shadow
    public abstract boolean isFood(ItemStack stack);

    @Shadow
    public abstract void resetLove();

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void onInteractMobWithFood(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        // Pass 87 (Lens 8/13): getWorstState below returns FRESH when the component is
        // absent, so the rotten check needs no work for component-less stacks. Check the
        // component first via the cheaper ItemStack.has() and skip the isSpoilable() CHM get.
        if (!stack.isEmpty() && stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)
                && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
            if (this.isFood(stack)) {
                if (FoodSpoilageUtil.getWorstState(stack) == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                    Animal animal = (Animal) (Object) this;
                    if (!animal.level().isClientSide()) {
                        SpoilageConfig.AnimalFeedingConfig animalCfg = SpoilageConfig.getInstance().getAnimalFeedingConfig();
                        if (animalCfg.rotten_cancels_breeding) {
                            this.resetLove();
                        }
                        if (animalCfg.rotten_poison_duration_ticks > 0) {
                            animal.addEffect(new MobEffectInstance(MobEffects.POISON, animalCfg.rotten_poison_duration_ticks, 0));
                        }
                        if (animalCfg.rotten_weakness_duration_ticks > 0) {
                            animal.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, animalCfg.rotten_weakness_duration_ticks, 0));
                        }

                        if (animalCfg.rotten_show_particles && animal.level() instanceof ServerLevel serverWorld) {
                            serverWorld.sendParticles(ParticleTypes.SMOKE, animal.getX(), animal.getY(0.5D), animal.getZ(),
                                    7, 0.2, 0.2, 0.2, 0.05);
                        }

                        if (!player.getAbilities().instabuild) {
                            com.spoilageenhanced.component.SpoilageData data = stack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
                            if (data != null) {
                                com.spoilageenhanced.component.SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
                                stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, split[0]);
                            }
                            stack.shrink(1);
                        }
                        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "AnimalEntityMixin: Fed rotten food to animal -> Applied Poison/Weakness.");
                    }
                    cir.setReturnValue(InteractionResult.SUCCESS);
                }
            }
        }
    }
}
