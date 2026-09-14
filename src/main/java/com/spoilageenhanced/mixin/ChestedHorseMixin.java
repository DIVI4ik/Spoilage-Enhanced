package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ages food inside a llama/donkey/mule chest.
 *
 * <p>Pass 1203 (L13 — observed behaviour): the chested horse's inventory is a
 * {@link SimpleContainer} (AbstractHorse.createInventory, AbstractHorse.java:302)
 * which does NOT call {@code inventoryTick} on its contents — the same gap the
 * allay had (pass 881, AllayMixin). Verified live: a bundle with a tracked
 * apple and a plain tracked apple in a llama's chest both read
 * {@code fresh_expirations:[100L]} unchanged after 25 seconds.</p>
 *
 * <p>The pass-848 verdict had closed this as "same gap class as the allay" while
 * believing the allay was fine; pass 881 found the allay WAS broken and fixed it,
 * which reopens this one — the class was real, not a no-op.</p>
 *
 * <p>{@code tick()} is declared in AbstractHorse (AbstractHorse.java:578) and not
 * overridden in AbstractChestedHorse, so the mixin targets AbstractHorse and
 * narrows with {@code instanceof AbstractChestedHorse} inside — the same shape
 * as ItemFrameMixin targeting BlockAttachedEntity. The cadence is the 20-tick
 * window offset by entity id (phase-spreading, same as ItemEntityMixin and
 * AllayMixin). The probe is the depth-2 food-carrying check (pass 1199), so a
 * bundle or nested shulker box in the chest is aged too, and unstamped food is
 * lazily stamped by {@code updateSpoilage} exactly as the allay branch does.</p>
 */
@Mixin(AbstractHorse.class)
public abstract class ChestedHorseMixin {

    @Shadow
    protected SimpleContainer inventory;

    @Inject(method = "tick", at = @At("RETURN"))
    private void spoilage_enhanced$ageChestContents(CallbackInfo ci) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (!(self instanceof AbstractChestedHorse)) {
            return;
        }
        if (self.level().isClientSide() || (self.getId() + self.tickCount) % 20 != 0) {
            return;
        }
        if (!self.isAlive()) {
            return;
        }
        // No chest, no inventory to age. hasChest() is on AbstractChestedHorse.
        if (!((AbstractChestedHorse) self).hasChest()) {
            return;
        }
        if (this.inventory == null) {
            return;
        }

        List<ItemStack> stacks = this.inventory.getItems();
        boolean anySpoilable = false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) continue;
            FoodSpoilageUtil.updateSpoilage(stack, self.level());
        }
    }
}
