package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A campfire must refuse rotten food, exactly as a furnace does.
 *
 * <p>Cooking deliberately refreshes food in this mod — that is what the furnace's smelt hook is
 * for — and the furnace pairs that with a refusal to accept anything rotten, which is why the
 * message is called "rotten cannot cook". A campfire had neither half of the deal: it has no
 * menu, so the guard living in the container-click path never saw it, and the cooked result is
 * assembled by vanilla with no spoilage data at all and reads as brand new.</p>
 *
 * <p>The effect was a laundry: drop rotten meat on a campfire, collect fresh cooked meat. Not a
 * corner case either — a campfire is the earliest cooker most players build.</p>
 *
 * <p>Placement takes one item, and the mod moves items worst-first everywhere else, so the
 * question asked is whether the worst single item is rotten. Stale food still cooks, as it does
 * in a furnace.</p>
 */
@Mixin(CampfireBlockEntity.class)
public class CampfireBlockEntityMixin {

    @Inject(
        method = "placeFood(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spoilage_enhanced$refuseRottenFood(ServerLevel level, LivingEntity source,
            ItemStack placeItem, CallbackInfoReturnable<Boolean> cir) {
        if (placeItem.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(placeItem.getItem())) {
            return;
        }
        if (!FoodSpoilageUtil.worstSliceContainsRotten(placeItem, 1)) {
            return;
        }

        // Returning false is what vanilla itself does when a stack has no campfire recipe, so
        // nothing is consumed and the item stays in hand.
        cir.setReturnValue(false);
        if (source instanceof Player player) {
            player.sendOverlayMessage(Component.translatable(
                    SpoilageEnhancedTranslations.CMD_ROTTEN_CANNOT_COOK));
        }
    }
}
