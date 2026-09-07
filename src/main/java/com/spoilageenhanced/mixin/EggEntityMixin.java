package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownEgg.class)
public abstract class EggEntityMixin extends ThrowableItemProjectile {

    public EggEntityMixin(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(
        method = "onHit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
            ordinal = 0
        )
    )
    private int spoilage_enhanced_preventRottenEggHatching(RandomSource random, int bound) {
        if (FoodSpoilageUtil.isRottenEgg(this.getItem(), this.level())) {
            if (this.level() instanceof ServerLevel serverWorld) {
                serverWorld.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(),
                        6, 0.1, 0.1, 0.1, 0.05);
                serverWorld.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(),
                        4, 0.1, 0.1, 0.1, 0.02);
            }
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS,
                    "EggEntityMixin: Rotten egg broken -> Chick hatching prevented (0% chance).");
            return 1;
        }
        return random.nextInt(bound);
    }
}
