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
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 405 (L13 observed): rotten gourd block placement refusal — what a player sees.
 *
 * <p>Scenario: player holds a rotten pumpkin, tries to place it. GourdBlockMixin
 * intercepts setPlacedBy and checks the worst state of the stack:
 * - worst state ROTTEN + block is eaten-in-place (CakeBlock) -> placement cancelled,
 *   block removed, item dropped back, player gets overlay message.
 * - worst state STALE/FRESH -> placement proceeds normally.
 *
 * The mixin needs Level/ServerLevel for world operations. This test pins the
 * rotten-detection logic by checking getWorstState on the stack before the mixin runs.
 *
 * What should happen: rotten pumpkin is detected via getWorstState == ROTTEN.
 */
public class GourdBlockPlacementTest {

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
    void rottenPumpkinIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten pumpkin, tries to place it
        ItemStack stack = new ItemStack(Items.PUMPKIN, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN for a stack with rottenCount=1");
    }

    @Test
    void mixedStackWithRottenIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten + 1 fresh pumpkin, tries to place
        ItemStack stack = new ItemStack(Items.PUMPKIN, 2);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN when any item is rotten (worst-first)");
    }

    @Test
    void stalePumpkinIsNotDetectedAsRotten() {
        // Scenario: player holds 1 stale pumpkin, tries to place it
        ItemStack stack = new ItemStack(Items.PUMPKIN, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return STALE (not ROTTEN) for a stale stack");
    }

    @Test
    void freshPumpkinIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh pumpkin, tries to place it
        ItemStack stack = new ItemStack(Items.PUMPKIN, 1);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a fresh stack");
    }

    @Test
    void pumpkinWithoutComponentIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh pumpkin (no component yet), tries to place
        ItemStack stack = new ItemStack(Items.PUMPKIN, 1);
        // No component set

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a component-less stack (treated as brand new)");
    }

    @Test
    void isEatenInPlaceReturnsTrueForCakeBlock() {
        // Scenario: check that CakeBlock is detected as eaten-in-place
        assertTrue(FoodSpoilageUtil.isEatenInPlace(Blocks.CAKE),
                "isEatenInPlace must return true for CakeBlock");
    }

    @Test
    void isEatenInPlaceReturnsFalseForPumpkin() {
        // Scenario: check that Pumpkin is NOT eaten-in-place (only CakeBlock is)
        assertFalse(FoodSpoilageUtil.isEatenInPlace(Blocks.PUMPKIN),
                "isEatenInPlace must return false for Pumpkin (not eaten in place)");
    }

    @Test
    void isEatenInPlaceReturnsFalseForMelon() {
        // Scenario: check that Melon is NOT eaten-in-place
        assertFalse(FoodSpoilageUtil.isEatenInPlace(Blocks.MELON),
                "isEatenInPlace must return false for Melon (not eaten in place)");
    }
}