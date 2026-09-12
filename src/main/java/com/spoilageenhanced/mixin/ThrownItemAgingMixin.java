package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside a thrown projectile (egg, snowball, etc.) while in flight.
 *
 * <p>ThrownItemEntityMixin only updates spoilage at creation (setItem), but
 * ThrowableProjectile.tick() runs every tick in flight and does NOT age the item.
 * Thrown food items (eggs, snowballs) did not age while in flight (pass 1149).</p>
 *
 * <p>This mixin injects at the RETURN of ThrowableItemProjectile.tick() (inherited
 * from ThrowableProjectile) and ages the held item stack if it is spoilable.
 * Uses the entity's position for phase-spreading so a swarm of thrown items
 * does not all age on the same tick boundary.</p>
 */
@Mixin(ThrowableItemProjectile.class)
public abstract class ThrownItemAgingMixin {

    @Inject(
        method = "tick",
        at = @At("RETURN"),
        require = 0
    )
    private void spoilage_enhanced_ageThrownItem(CallbackInfo ci) {
        ThrowableItemProjectile projectile = (ThrowableItemProjectile) (Object) this;
        Level level = projectile.level();
        if (level.isClientSide()) {
            return;
        }
        // Phase-spread by entity position + game time (same pattern as ItemEntityMixin)
        if (FoodSpoilageUtil.shouldSkipAgingTick(projectile.blockPosition(), level.getGameTime())) {
            return;
        }

        ItemStack itemStack = projectile.getItem();
        if (itemStack.isEmpty()) {
            return;
        }
        if (!SpoilageConfig.getInstance().isSpoilable(itemStack.getItem())) {
            return;
        }

        FoodSpoilageUtil.updateSpoilage(itemStack, level);
        projectile.setItem(itemStack);
    }
}
