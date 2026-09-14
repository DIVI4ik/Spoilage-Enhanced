package com.spoilageenhanced.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside a villager's inventory.
 *
 * <p>Pass 1204 (L13 — observed behaviour): the villager's inventory is a
 * {@link SimpleContainer} (AbstractVillager.java:60) which does NOT call
 * {@code inventoryTick} on its contents — the same gap the allay (pass 881)
 * and the chested horse (pass 1203) had. Verified live: a tracked apple in a
 * villager's Inventory read {@code fresh_expirations:[100L]} unchanged after
 * 25 seconds. A villager picks up food via
 * {@code InventoryCarrier.pickUpItem} (Villager.java:774) and holds it until
 * it eats — that held food never aged.</p>
 *
 * <p>AbstractVillager declares no tick(), so the hook goes into
 * Villager.tick() (Villager.java:278); the wandering trader gets its own thin
 * mixin (WanderingTraderInventoryMixin) delegating to the same body,
 * VillagerInventoryAging.</p>
 */
@Mixin(Villager.class)
public abstract class VillagerInventoryMixin {

    // The inventory field is declared in AbstractVillager (private, AbstractVillager.java:60),
    // not in Villager, so it cannot be @Shadow'd here — the public getInventory()
    // (AbstractVillager.java:220) reaches it.
    @Inject(method = "tick", at = @At("RETURN"))
    private void spoilage_enhanced$ageInventoryContents(CallbackInfo ci) {
        com.spoilageenhanced.util.VillagerInventoryAging.ageInventory((AbstractVillager) (Object) this,
                ((AbstractVillager) (Object) this).getInventory());
    }
}
