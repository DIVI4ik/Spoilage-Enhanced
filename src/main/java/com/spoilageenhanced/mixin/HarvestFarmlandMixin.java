package com.spoilageenhanced.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Villagers must not plant rotten produce either.
 *
 * <p>The refusal in {@code GourdBlockMixin.setPlacedBy} covers everything that places a block
 * through an item — which is every player action, and dispensers too, since no vanilla dispense
 * behaviour plants seeds (only the shulker box places a block, and that is not food). Villagers
 * are the exception: {@code HarvestFarmland} calls {@code level.setBlockAndUpdate} directly and
 * never touches {@code setPlacedBy}, so without this a farmer would happily plant a rotten
 * potato and harvest a fresh one — laundering rot on a loop, unattended.</p>
 *
 * <p>The hook is on the tag test that decides whether a stack counts as plantable, rather than
 * on the placement itself. Cancelling the placement would leave the surrounding code believing
 * it had planted: it sets its success flag, plays the sound and shrinks the stack, so the
 * rotten potato would be destroyed instead of kept. Answering "not plantable" makes the
 * villager skip that inventory slot and look at the next one, which is exactly the behaviour
 * wanted and needs no repair afterwards.</p>
 *
 * <p>There is exactly one such call in {@code tick}, so the target is unambiguous.</p>
 */
@Mixin(HarvestFarmland.class)
public class HarvestFarmlandMixin {

    @WrapOperation(
        method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"
        )
    )
    private boolean spoilage_enhanced$refuseRottenSeed(ItemStack stack, TagKey<Item> tag,
            Operation<Boolean> original) {
        if (!original.call(stack, tag)) {
            return false;
        }
        return FoodSpoilageUtil.getWorstState(stack) != FoodSpoilageUtil.SpoilageState.ROTTEN;
    }
}
