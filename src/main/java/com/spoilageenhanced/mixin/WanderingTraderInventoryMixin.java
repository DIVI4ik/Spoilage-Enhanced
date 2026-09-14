package com.spoilageenhanced.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pass 1204: the wandering trader half of the villager-inventory aging gap.
 * WanderingTrader.tick() is declared at WanderingTrader.java:250; the body is
 * shared with VillagerInventoryMixin via VillagerInventoryAging.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderInventoryMixin {

    // Same as VillagerInventoryMixin: the field lives in AbstractVillager, use
    // the public getInventory() (AbstractVillager.java:220). WanderingTrader
    // declares no tick() of its own (the tick at WanderingTrader.java:250 belongs
    // to an inner Goal class) — aiStep() (WanderingTrader.java:204) is the
    // per-tick method it does declare.
    @Inject(method = "aiStep", at = @At("RETURN"))
    private void spoilage_enhanced$ageInventoryContents(CallbackInfo ci) {
        com.spoilageenhanced.util.VillagerInventoryAging.ageInventory((AbstractVillager) (Object) this,
                ((AbstractVillager) (Object) this).getInventory());
    }
}
