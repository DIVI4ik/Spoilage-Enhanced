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
import net.minecraft.world.level.block.ComposterBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 406 (L13 observed): rotten composter behavior — what a player sees.
 *
 * <p>Scenario: player adds rotten apple to composter. ComposterBlockMixin intercepts
 * the composter interaction and adjusts the compost chance based on the worst state:
 * - ROTTEN -> uses composterConfig.rotten_chance (lowest, often 0)
 * - STALE -> uses max(composterConfig.stale_chance, defaultChance)
 * - FRESH -> uses min(composterConfig.fresh_chance, defaultChance)
 *
 * The mixin needs Level for world.getRandom(). This test pins the worst-state
 * detection logic by checking getWorstState on the stack before the mixin runs.
 *
 * What should happen: rotten food is detected via getWorstState == ROTTEN.
 */
public class ComposterLogicTest {

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
    void rottenAppleIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten apple, adds to composter
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN for a stack with rottenCount=1");
    }

    @Test
    void mixedStackWithRottenIsDetectedAsRotten() {
        // Scenario: player holds 1 rotten + 1 fresh apple, adds to composter
        ItemStack stack = new ItemStack(Items.APPLE, 2);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return ROTTEN when any item is rotten (worst-first)");
    }

    @Test
    void staleAppleIsNotDetectedAsRotten() {
        // Scenario: player holds 1 stale apple, adds to composter
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return STALE (not ROTTEN) for a stale stack");
    }

    @Test
    void freshAppleIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh apple, adds to composter
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a fresh stack");
    }

    @Test
    void appleWithoutComponentIsNotDetectedAsRotten() {
        // Scenario: player holds 1 fresh apple (no component yet), adds to composter
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        // No component set

        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(stack),
                "getWorstState must return FRESH for a component-less stack (treated as brand new)");
    }

    @Test
    void extractWorstItemsRemovesOneFromStack() {
        // Scenario: composter adds 1 item at a time, extractWorstItems(1) must reduce
        // the stack's trackers by 1
        ItemStack stack = new ItemStack(Items.APPLE, 3);
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 10000L, System.currentTimeMillis() + 20000L, System.currentTimeMillis() + 30000L),
                List.of(),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        // split[0] = remaining (after taking 1 worst)
        // split[1] = extracted (1 worst item)
        assertEquals(2, split[0].freshExpirations().size(),
                "Remaining must have 2 fresh entries (3 - 1)");
        assertEquals(1, split[1].freshExpirations().size(),
                "Extracted must have 1 fresh entry (the worst)");
    }

    @Test
    void composterDefaultChanceForAppleIsPositive() {
        // Scenario: verify the default compost chance for apple is positive
        // (otherwise the mixin returns early before checking spoilage)
        float defaultChance = ComposterBlock.COMPOSTABLES.getFloat(Items.APPLE);
        assertTrue(defaultChance > 0.0F,
                "Default compost chance for apple must be positive, got " + defaultChance);
    }
}