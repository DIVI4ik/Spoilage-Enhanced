package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.blay09.mods.cookingforblockheads.block.entity.CounterBlockEntity;

/**
 * Ages food inside a Cooking for Blockheads counter.
 *
 * <p>Gap (pass 1261, L14 — foreign content): the counter's {@code serverTick} only handles door
 * animation and sync; it never calls {@code Item.inventoryTick} on its contents, so food inside
 * never ages — the same container-aging gap class as the brewing stand (fixed in 0cf47bb), the
 * bundle (pass 845), the chest minecart (pass 846), and the fridge (pass 1049). Verified live:
 * a tracked apple placed in the counter retained its initial freshness timestamp indefinitely
 * after 35+ seconds, while the same apple in the fridge (which has FridgeBlockEntityMixin)
 * aged to rotten_count:1.</p>
 *
 * <p><b>Why a string target.</b> Cooking for Blockheads is a runtime-only dependency — its jar
 * is never on the compile classpath (L14 rule: zero foreign compile dependencies), so the class
 * cannot be referenced as a {@code .class} literal. {@code targets = "..."} resolves the class
 * by name at application time; with {@code require = 0} the mixin is skipped silently on any
 * installation without the mod. The mixin logs its first application (project rule: mixins with
 * {@code require = 0} must log).</p>
 *
 * <p><b>The contract, not the mod.</b> The aging branch reads the counter's inventory through
 * the vanilla {@code Container} interface the counter implements ({@code getContainer()} is
 * inherited from {@code BalmContainerProvider} -> {@code Container}). It ages every spoilable
 * stack on the same 20-tick phase-spread cadence as {@code ItemEntityMixin} and
 * {@code BrewingStandBlockEntityMixin}. Nothing here names a Cooking for Blockheads item or
 * block; the same mixin shape would age any mod's counter.</p>
 */
@Mixin(targets = "net.blay09.mods.cookingforblockheads.block.entity.CounterBlockEntity")
public abstract class CounterBlockEntityMixin {

    @Shadow
    public abstract Container getContainer();

    @Unique
    private static boolean spoilage_enhanced_loggedApplication = false;

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private static void spoilage_enhanced$ageCounterContents(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            net.blay09.mods.cookingforblockheads.block.entity.CounterBlockEntity blockEntity, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (!spoilage_enhanced_loggedApplication) {
            spoilage_enhanced_loggedApplication = true;
            SpoilageEnhancedLogger.log("CounterBlockEntityMixin applied (Cooking for Blockheads present)");
        }
        // Phase-spread by position, same as BrewingStandBlockEntityMixin.
        if (com.spoilageenhanced.util.FoodSpoilageUtil.shouldSkipAgingTick(pos, level.getGameTime())) {
            return;
        }

        Container container = ((CounterBlockEntityMixin) (Object) blockEntity).getContainer();
        int slots = container.getContainerSize();
        // Pass 1192: shared probe — see ContainerAgingSweepMixin.ageContainer.
        boolean anySpoilable = false;
        for (int i = 0; i < slots; i++) {
            if (FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(container.getItem(i))) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (int i = 0; i < slots; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            // Pass 1191: updateSpoilage no-ops on stacks that are neither spoilable nor
            // food-carrying (its own guards), so no isSpoilable filter here.
            FoodSpoilageUtil.updateSpoilage(stack, level);
        }
    }
}