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
 * The Forge-only seven-argument {@code Block.dropResources}.
 *
 * <p>Forge adds a trailing {@code boolean} to {@code dropResources} and rewrites
 * {@code Block.playerDestroy} to call that overload. Verified on the patched jar:</p>
 *
 * <pre>
 * 28: invokestatic dropResources:(…BlockState;Level;BlockPos;BlockEntity;Entity;ItemStack;Z)V
 * </pre>
 *
 * <p>So on Forge a player breaking a block never reaches the vanilla six-argument method the other
 * mixin hooks, and the block's spoilage was silently lost — a stale pumpkin dropped a fresh one.
 * The vanilla jar has no such overload, hence {@code require = 0}: on Fabric and NeoForge this
 * mixin simply has nothing to attach to, and {@link BlockDropSpoilageMixin} covers the path
 * instead.</p>
 *
 * <p>Because {@code require = 0} fails silently, whether this applied is only ever visible in the
 * log: the handler tags every capture and every applied drop with the overload it came through.</p>
 */
@Mixin(value = Block.class, priority = 1100)
public abstract class BlockDropSpoilageForgeMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("HEAD"), require = 0)
    private static void spoilage_enhanced$beforeForgeDrop(BlockState state, Level world, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool, boolean dropXp, CallbackInfo ci) {
        BlockDropSpoilageHandler.before(state, world, pos, "forge 7-arg");
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("TAIL"), require = 0)
    private static void spoilage_enhanced$afterForgeDrop(BlockState state, Level world, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool, boolean dropXp, CallbackInfo ci) {
        BlockDropSpoilageHandler.after(state, world, pos, "forge 7-arg");
    }
}
