package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void spoilage_enhanced_onTakeCraftedItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide())
            return;
        if (stack.isEmpty())
            return;

        List<ItemStack> ingredients = new ArrayList<>(this.craftSlots.getContainerSize());
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ingredients.add(this.craftSlots.getItem(i));
        }

        CraftingSpoilageTransfer.Result transfer =
                CraftingSpoilageTransfer.compute(ingredients, stack, player.level().getGameTime());
        if (transfer == null) return;

        stack.set(ModDataComponentTypes.SPOILAGE, transfer.data());
        SpoilageEnhancedLogger.log("Crafting: Proportional state transferred from " + transfer.source().getItem()
                + " to " + stack.getItem() + " (State: " + transfer.state() + ")");
    }
}
