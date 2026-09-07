package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.RandomizableContainerHelper;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerBlockEntityMixin extends RandomizableContainer {

    @Inject(method = "unpackLootTable", at = @At("RETURN"))
    default void onGenerateLoot(@Nullable Player player, CallbackInfo ci) {
        if (Boolean.TRUE.equals(RandomizableContainerHelper.IS_RANDOMIZING.get())) {
            return;
        }

        Level world = this.getLevel();
        if (world == null || world.isClientSide())
            return;

        RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.TRUE);
        try {
            RandomSource rand = world.getRandom();
            for (int i = 0; i < this.getContainerSize(); i++) {
                ItemStack stack = this.getItem(i);
                if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                    continue;
                }

                SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
                if (data == null || data.isEmpty()) {
                    FoodSpoilageUtil.randomizeSpoilage(stack, world, rand);
                    this.setItem(i, stack);
                    SpoilageEnhancedLogger.log("Slot " + i + ": randomized " + stack.getItem().getName(ItemStack.EMPTY).getString());
                }
            }
        } catch (Exception e) {
            SpoilageEnhancedLogger.log("Exception during randomize: " + e.toString());
        } finally {
            RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.FALSE);
        }
    }
}
