package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside a player's ender chest (pass 1184, L13 — observed behaviour).
 *
 * <p>{@code Player.tick()} calls {@code this.inventory.tick()} (Player.java:448), which
 * calls {@code inventoryTick} on the 36 main slots only (Inventory.java:242-249). The
 * ender chest is a separate {@code PlayerEnderChestContainer} that nothing ever ticks:
 * {@code EnderChestBlock.getTicker} returns null server-side (EnderChestBlock.java:110,
 * the lid animation is client-only), and the container lives on the player, not on the
 * block, so {@code ContainerAgingSweepMixin} — which walks block entities — cannot
 * reach it either. Food in an ender chest was therefore a perfect freezer while the same
 * food in the player's main inventory aged every second.</p>
 *
 * <p>The cadence is the same 20-tick window the other container paths use, phase-spread
 * by the player's entity id so a server full of players does not all age on the same
 * boundary (same phase-spreading as {@code ItemEntityMixin} and
 * {@code AbstractMinecartContainerMixin}). The probe-then-update shape mirrors
 * {@code HopperAgingMixin}: the cheap {@code isSpoilable} probe runs before any
 * component access, and {@code updateSpoilage} lazily stamps unstamped spoilable food
 * exactly as the bundle and minecart branches do.</p>
 *
 * <p><b>Offline parity with vanilla.</b> The ender chest ages only while the player is
 * online, because {@code Player.tick} does not run for offline players. That is exactly
 * how vanilla treats the main inventory too — nothing ticks an offline player — so the
 * ender chest is no more a freezer than the player's own backpack is.</p>
 */
@Mixin(Player.class)
public abstract class PlayerEnderChestMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void spoilage_enhanced$ageEnderChestContents(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        Level world = self.level();
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
            return;
        }
        // Pass 1189 (L12 — claim drift): the javadoc claimed phase-spreading by entity id
        // "same as ItemEntityMixin", but the gate was bare tickCount % 20 — every player's
        // ender chest aged on the same boundary, and tickCount resets to 0 on entity load,
        // so simultaneously-relogging players aligned too. Match the documented claim.
        if ((self.getId() + self.tickCount) % 20 != 0) {
            return;
        }

        var enderChest = self.getEnderChestInventory();
        boolean anySpoilable = false;
        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);
            if (!stack.isEmpty() && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            // One corrupted stack must not kill Player.tick: guard per slot, log, continue.
            try {
                ItemStack stack = enderChest.getItem(i);
                if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                    continue;
                }
                FoodSpoilageUtil.updateSpoilage(stack, serverWorld);
            } catch (Throwable t) {
                SpoilageEnhancedLogger.log("PlayerEnderChestMixin: skipped ender chest slot "
                        + i + " for " + self.getName().getString() + ": " + t);
            }
        }
    }
}
