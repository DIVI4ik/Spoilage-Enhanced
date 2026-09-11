package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

/**
 * Ages food inside a Farmer's Delight cooking pot.
 *
 * <p>Gap (pass 1048, L14 — foreign content): the pot's {@code cookingTick} only advances cook
 * time; it never calls {@code Item.inventoryTick} on its contents, so ingredients and the
 * finished meal sit frozen — the same container-aging gap class as the brewing stand (fixed in
 * 0cf47bb), the bundle (pass 845) and the chest minecart (pass 846). Verified live: after
 * cooking vegetable_soup, slot 6 read {@code {count: 1, Slot: 6b, id: "farmersdelight:vegetable_soup"}}
 * with no spoilage component, and it never gained one.</p>
 *
 * <p><b>Why a string target.</b> Farmer's Delight is a runtime-only dependency — its jar is
 * never on the compile classpath (L14 rule: zero foreign compile dependencies), so the class
 * cannot be referenced as a {@code .class} literal. {@code targets = "..."} resolves the class
 * by name at application time; with {@code require = 0} the mixin is skipped silently on any
 * installation without the mod, which is every vanilla and Forge/NeoForge run. The mixin logs
 * its first application (project rule: mixins with {@code require = 0} must log), so a log
 * grep tells whether it is live.</p>
 *
 * <p><b>The contract, not the mod.</b> The aging branch reads the pot's inventory through the
 * two-method shape of its own handler ({@code getSlotCount}/{@code getStackInSlot}, stubbed
 * for compilation only) and ages every spoilable stack on the same 20-tick phase-spread
 * cadence as {@code ItemEntityMixin} and {@code BrewingStandBlockEntityMixin}. Nothing here
 * names a Farmer's Delight item or block; the same mixin shape would age any mod's pot.</p>
 *
 * <p>The meal-stamping half of the original gap is covered by this aging branch itself:
 * {@code FoodSpoilageUtil.updateSpoilage} lazily stamps a fresh timer on any spoilable stack
 * that has none, exactly as the bundle and minecart branches do — so the assembled meal gains
 * its timer on the first aging pass after cooking, without hooking the recipe assembly.</p>
 */
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity")
public abstract class CookingPotBlockEntityMixin {

    @Shadow
    public abstract ItemStackHandler getInventory();

    @Unique
    private static boolean spoilage_enhanced_loggedApplication = false;

    @Inject(method = "cookingTick", at = @At("RETURN"), require = 0)
    private static void spoilage_enhanced$agePotContents(ServerLevel level, BlockPos pos, BlockState state,
            vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity blockEntity, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (!spoilage_enhanced_loggedApplication) {
            spoilage_enhanced_loggedApplication = true;
            SpoilageEnhancedLogger.log("CookingPotBlockEntityMixin applied (Farmer's Delight present)");
        }
        // Phase-spread by position, same as BrewingStandBlockEntityMixin: a row of pots does
        // not all age on the same tick boundary.
        if ((pos.getX() + pos.getZ() + level.getGameTime()) % 20 != 0) {
            return;
        }

        ItemStackHandler inventory = ((CookingPotBlockEntityMixin) (Object) blockEntity).getInventory();
        int slots = inventory.getSlotCount();
        boolean anySpoilable = false;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && com.spoilageenhanced.config.SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (int i = 0; i < slots; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!com.spoilageenhanced.config.SpoilageConfig.getInstance().isSpoilable(stack.getItem())) continue;
            FoodSpoilageUtil.updateSpoilage(stack, level);
        }
    }
}
