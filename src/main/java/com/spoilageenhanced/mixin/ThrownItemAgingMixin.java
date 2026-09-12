package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
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
 * <p>ThrowableItemProjectile does NOT declare {@code tick()} — it is declared in
 * ThrowableProjectile (ThrowableProjectile.java:46), and neither Snowball nor
 * ThrowableItemProjectile override it. A mixin on ThrowableItemProjectile with
 * {@code method = "tick"} finds no target and (with require=0) silently skips —
 * exactly the ItemFrameMixin failure shape. So the mixin targets
 * ThrowableProjectile and narrows with {@code instanceof ThrowableItemProjectile}
 * inside.</p>
 *
 * <p>ThrownItemEntityMixin only updates spoilage at creation (setItem); this
 * mixin ages the held item every 20 ticks while in flight (pass 1149).</p>
 */
@Mixin(ThrowableProjectile.class)
public abstract class ThrownItemAgingMixin {

    @Inject(
        method = "tick",
        at = @At("RETURN"),
        require = 1
    )
    private void spoilage_enhanced_ageThrownItem(CallbackInfo ci) {
        if (!((Object) this instanceof ThrowableItemProjectile projectile)) {
            return;
        }
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
