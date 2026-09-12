package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside an ItemFrame at the same 20-tick cadence as other containers.
 *
 * <p>ItemFrame extends HangingEntity extends BlockAttachedEntity, and it is
 * BlockAttachedEntity that declares {@code tick()} (BlockAttachedEntity.java:39) —
 * ItemFrame and HangingEntity do not override it. A mixin on ItemFrame with
 * {@code method = "tick"} finds no target (the first launch failed with
 * "could not find any targets matching 'tick' in ItemFrame"), so the mixin must
 * target BlockAttachedEntity and narrow with {@code instanceof ItemFrame} inside.
 *
 * <p>Armor stands (LivingEntity) age their equipment via LivingEntity.tick() ->
 * equipment.tick() -> inventoryTick (hooked by ItemMixin). Item frames were a
 * "perfect freezer" — food never aged while displayed (PLAYER_REPORTS §9).
 */
@Mixin(BlockAttachedEntity.class)
public abstract class ItemFrameMixin {

    @Inject(
        method = "tick",
        at = @At("RETURN"),
        require = 1
    )
    private void spoilage_enhanced_ageHeldItem(CallbackInfo ci) {
        if (!((Object) this instanceof ItemFrame frame)) {
            return;
        }
        if (frame.level().isClientSide()) {
            return;
        }
        if (FoodSpoilageUtil.shouldSkipAgingTick(frame.blockPosition(), frame.level().getGameTime())) {
            return;
        }

        ItemStack itemStack = frame.getItem();
        if (itemStack.isEmpty()) {
            return;
        }
        if (!SpoilageConfig.getInstance().isSpoilable(itemStack.getItem())) {
            return;
        }

        FoodSpoilageUtil.updateSpoilage(itemStack, frame.level());
        frame.setItem(itemStack);
    }
}
