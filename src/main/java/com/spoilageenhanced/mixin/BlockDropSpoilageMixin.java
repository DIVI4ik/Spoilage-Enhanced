package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockDropSpoilageHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries block spoilage onto the dropped items, for the vanilla six-argument
 * {@code Block.dropResources}.
 *
 * <p>This is the overload {@code Level.destroyBlock} uses, and the one a player break goes through
 * on Fabric. On Forge the player path uses a seven-argument overload instead — see
 * {@link BlockDropSpoilageForgeMixin}. Both funnel into
 * {@link BlockDropSpoilageHandler}, which ignores the inner call when one delegates to the
 * other.</p>
 */
@Mixin(Block.class)
public abstract class BlockDropSpoilageMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void beforeDropStacks(BlockState state, Level world, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        BlockDropSpoilageHandler.before(state, world, pos, "vanilla 6-arg");
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void afterDropStacks(BlockState state, Level world, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        BlockDropSpoilageHandler.after(state, world, pos, "vanilla 6-arg");
    }
}
