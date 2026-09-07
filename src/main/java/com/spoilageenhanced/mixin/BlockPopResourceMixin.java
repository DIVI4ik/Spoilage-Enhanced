package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockDropSpoilageHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps a broken block's spoilage onto the stack it drops.
 *
 * <p>{@link BlockDropSpoilageMixin} captures the state at the start of {@code dropResources} and
 * used to write it onto the {@link net.minecraft.world.entity.item.ItemEntity}s found nearby at the
 * end. On vanilla those entities exist by then; on Forge they do not, because Forge captures block
 * drops into a list and spawns them after the call returns. The state was read correctly and then
 * applied to nothing, so a stale pumpkin still dropped a fresh one.</p>
 *
 * <p>{@code popResource(Level, BlockPos, ItemStack)} is identical on both loaders and runs while
 * the stack is still a stack, so stamping here works everywhere. The entity scan stays as a safety
 * net for drop paths that bypass this method; it skips anything already stamped.</p>
 */
@Mixin(Block.class)
public abstract class BlockPopResourceMixin {

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private static void spoilage_enhanced$stampDroppedStack(Level level, BlockPos pos, ItemStack stack,
            CallbackInfo ci) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockDropSpoilageHandler.stampPending(stack);
    }
}
