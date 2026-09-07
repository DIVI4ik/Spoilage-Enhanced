package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.ActiveInteractionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class InteractionManagerMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void beforeInteractBlock(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        ActiveInteractionContext.start(pos, state, world.getGameTime());
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void afterInteractBlock(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        ActiveInteractionContext.clear();
    }
}
