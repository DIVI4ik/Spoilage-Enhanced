package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 410 (L13 observed): rotten milk effect behavior — what a player sees.
 *
 * <p>Scenario: player drinks rotten milk. ClearAllStatusEffectsConsumeEffectMixin
 * intercepts the milk bucket's consume effect and checks the worst state:
 * - FRESH -> vanilla behavior (clear all effects)
 * - STALE -> apply nausea, then clear effects
 * - ROTTEN -> apply nausea + poison + hunger, optionally block effect clearing
 *
 * The mixin needs Level and LivingEntity. This test pins the rotten-detection
 * logic by checking getWorstState on the stack before the mixin runs.
 *
 * What should happen: rotten milk is detected via getWorstState == ROTTEN.
 */
public class RottenMilkEffectTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void rottenMilkBucketIsDetectedAsRotten() {
        // Scenario: player drinks 1 rotten milk bucket
        ItemStack stack = new ItemStack(Items.MILK_BUCKET, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN for a stack with rottenCount=1");
    }

    @Test
    void staleMilkBucketIsDetectedAsStale() {
        // Scenario: player drinks 1 stale milk bucket
        ItemStack stack = new ItemStack(Items.MILK_BUCKET, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return STALE for a stale stack");
    }

    @Test
    void freshMilkBucketIsDetectedAsFresh() {
        // Scenario: player drinks 1 fresh milk bucket
        ItemStack stack = new ItemStack(Items.MILK_BUCKET, 1);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a fresh stack");
    }

    @Test
    void milkBucketWithoutComponentIsDetectedAsFresh() {
        // Scenario: player drinks 1 fresh milk bucket (no component yet)
        ItemStack stack = new ItemStack(Items.MILK_BUCKET, 1);
        // No component set

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a component-less stack (treated as brand new)");
    }
}