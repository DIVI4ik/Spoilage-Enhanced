package com.spoilageenhanced.util;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared aging body for the villager-inventory gap (pass 1204). AbstractVillager
 * declares no tick() of its own, so the hook must be injected into each
 * subclass's tick (Villager.java:278, WanderingTrader.java:250); both delegate
 * here so the logic exists once.
 */
public final class VillagerInventoryAging {

    private VillagerInventoryAging() {
    }

    public static void ageInventory(AbstractVillager self, SimpleContainer inventory) {
        if (self.level().isClientSide() || (self.getId() + self.tickCount) % 20 != 0) {
            return;
        }
        if (!self.isAlive() || inventory == null) {
            return;
        }

        List<ItemStack> stacks = inventory.getItems();
        boolean anySpoilable = false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) continue;
            FoodSpoilageUtil.updateSpoilage(stack, self.level());
        }
    }
}
