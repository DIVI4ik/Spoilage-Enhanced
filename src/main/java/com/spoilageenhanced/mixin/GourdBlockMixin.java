package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockSpoilageData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class GourdBlockMixin {

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void onBlockPlaced(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack,
             CallbackInfo ci) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld))
            return;

        String dropItemId = com.spoilageenhanced.util.DynamicFoodBlockCache.getFoodDrop(state, serverWorld, pos);

        boolean isSpoilable = false;
        if (dropItemId != null) {
            isSpoilable = true;
        } else {
            Item blockItem = state.getBlock().asItem();
            if (blockItem != null && blockItem != Items.AIR && com.spoilageenhanced.config.SpoilageConfig.getInstance().isSpoilable(blockItem)) {
                isSpoilable = true;
            }
        }
        
        // Rotten food cannot be planted. Anything else can - a stale potato still grows.
        //
        // Without this, rot was a free reset: plant the spoiled potato, wait, harvest a fresh
        // one. Refused the same way a rotten cake is, and the item is handed back rather than
        // eaten, because vanilla consumes one straight after this callback.
        if (com.spoilageenhanced.util.FoodSpoilageUtil.growthProperty(state) != null
                && com.spoilageenhanced.util.FoodSpoilageUtil.getWorstState(itemStack)
                        == com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState.ROTTEN) {
            world.removeBlock(pos, false);
            if (placer != null && !placer.hasInfiniteMaterials()) {
                itemStack.grow(1);
            }
            if (placer instanceof net.minecraft.world.entity.player.Player player) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        com.spoilageenhanced.util.SpoilageEnhancedTranslations.CMD_ROTTEN_CANNOT_COOK));
            }
            return;
        }

        // A crop that has just been planted is not food yet. Registering it here started its
        // freshness clock at planting, so it aged through the entire growth period and could be
        // stale before it was ever harvestable. BlockStateChangeMixin picks it up instead, at
        // the moment it finishes growing.
        //
        // The stack still has to give up the entry for the item that was planted. Vanilla takes
        // the item either way, so returning without doing that leaves the stack holding three
        // spoilage entries for two remaining potatoes - the same drift that made three pumpkins
        // placed from a mixed stack all come out rotten.
        if (com.spoilageenhanced.util.FoodSpoilageUtil.isImmatureCrop(state)) {
            spoilage_enhanced$consumePlacedEntry(itemStack, placer);
            return;
        }

        if (isSpoilable) {
            // BUG-12: inherit the WORST item's condition, not the best. A stack of 3 pumpkins
            // where one is rotten must place a rotten block; using the best state laundered the
            // spoilage away (rotten pumpkin placed -> fresh pumpkin mined back out).
            com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState worstState = com.spoilageenhanced.util.FoodSpoilageUtil.getWorstState(itemStack);
            long expirationTime = com.spoilageenhanced.util.FoodSpoilageUtil.getWorstTimestamp(itemStack, worstState);

            // A rotten cake CAN be placed, and eating it is what punishes you.
            //
            // Pass 224 refused the placement instead, for a real reason: a block eaten in place
            // never passes through ItemStackMixin, where the rotten effects live, so a rotten
            // cake placed and eaten handed out clean food. Refusing was the cheap way to close
            // that hole, and it closed the wrong thing — the player could no longer put a
            // spoiled cake down at all, which is not what spoilage is supposed to mean.
            //
            // CakeEatMixin now applies the effects on the block-eating path itself, reading the
            // condition recorded below, so the laundering hole is shut where it actually was
            // and the cake stays placeable.
            if (worstState == com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState.ROTTEN) {
                BlockSpoilageData.get(serverWorld).setSpoilageState(pos, worstState, -1);
            } else if (expirationTime != -1) {
                BlockSpoilageData.get(serverWorld).setSpoilageState(pos, worstState, expirationTime);
            } else {
                long freshDuration = com.spoilageenhanced.config.SpoilageConfig.getInstance().getFreshDurationForItem(itemStack.getItem());
                BlockSpoilageData.get(serverWorld).setSpoilageState(pos, com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState.FRESH, world.getGameTime() + freshDuration);
            }

            // Take the placed item's entry OUT of the stack. Without this the block copied the
            // worst state and the stack kept every entry it had, so placing three pumpkins that
            // were fresh, stale and rotten produced three ROTTEN blocks: the count dropped each
            // time, the entries did not, and getWorstState answered ROTTEN on all three.
            //
            // Worst-first matches the rest of the mod - slots, hoppers and crafting all move
            // items through extractWorstItems - and it is what worstState above already read,
            // so split[1] is exactly the entry just placed and split[0] is what stays in hand.
            //
            // Vanilla shrinks the stack straight after this callback (BlockItem.place:80 calls
            // setPlacedBy, :89 calls consume), so removing one entry here leaves entries and
            // count in step. Creative is the exception: ItemStack.consume skips the shrink for
            // a player with infinite materials, and removing an entry there would eat the
            // stack's spoilage one placement at a time while the count never moved.
            spoilage_enhanced$consumePlacedEntry(itemStack, placer);
        }
    }

    /**
     * Remove the placed item's spoilage entry from the stack it came out of.
     *
     * <p>Worst-first, matching how slots, hoppers and crafting all move items, so the entry taken
     * is the one whose condition was just written onto the block.</p>
     *
     * <p>Vanilla shrinks the stack immediately after this callback (BlockItem.place calls
     * setPlacedBy, then consume), so removing one entry here keeps entries and count in step.
     * Skipping it is what made three pumpkins from a fresh/stale/rotten stack all come out
     * rotten: the count fell each time and the entries did not, so the worst stayed rotten.</p>
     *
     * <p>Creative is the exception - {@code ItemStack.consume} skips the shrink for a player with
     * infinite materials, so removing an entry there would eat the stack's spoilage one placement
     * at a time while the count never moved.</p>
     */
    @org.spongepowered.asm.mixin.Unique
    private static void spoilage_enhanced$consumePlacedEntry(ItemStack itemStack, LivingEntity placer) {
        if (placer != null && placer.hasInfiniteMaterials()) {
            return;
        }
        com.spoilageenhanced.component.SpoilageData data =
                itemStack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) {
            return;
        }
        com.spoilageenhanced.component.SpoilageData[] split =
                com.spoilageenhanced.util.FoodSpoilageUtil.extractWorstItems(data, 1);
        itemStack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, split[0]);
    }
}
