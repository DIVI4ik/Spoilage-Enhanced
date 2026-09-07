package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
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
 * Pass 404 (L13 observed): rotten food feeding behavior — what a player sees.
 *
 * <p>Scenario: player tries to feed a rotten apple to a cow. The animal feeding
 * guard checks the worst state of the stack:
 * - worst state ROTTEN -> breeding is cancelled, poison/weakness applied
 * - worst state STALE -> breeding proceeds normally (stale food is acceptable)
 * - worst state FRESH -> breeding proceeds normally
 *
 * The mixin (AnimalEntityMixin) needs Level and ServerLevel for particles/effects.
 * This test pins the rotten-detection logic by checking getWorstState on the stack
 * before the mixin runs.
 *
 * What should happen: rotten food is detected via getWorstState == ROTTEN.
 */
public class RottenFeedingLogicTest {

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
    void rottenStackIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten apple, tries to feed cow
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN for a stack with rottenCount=1");
    }

    @Test
    void mixedStackWithRottenIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten + 1 fresh apple, tries to feed cow
        ItemStack stack = new ItemStack(Items.EGG, 2);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN when any item is rotten (worst-first)");
    }

    @Test
    void staleStackIsNotDetectedAsRotten() {
        // Scenario: player holds 1 stale apple, tries to feed cow
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return STALE (not ROTTEN) for a stale stack");
    }

    @Test
    void freshStackIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh apple, tries to feed cow
        ItemStack stack = new ItemStack(Items.EGG, 1);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a fresh stack");
    }

    @Test
    void stackWithoutComponentIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh apple (no component yet), tries to feed cow
        ItemStack stack = new ItemStack(Items.EGG, 1);
        // No component set

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a component-less stack (treated as brand new)");
    }

    @Test
    void emptyStackIsNotDetectedAsRotten() {
        // Scenario: player holds empty hand, tries to feed cow
        ItemStack stack = ItemStack.EMPTY;

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for an empty stack");
    }
}