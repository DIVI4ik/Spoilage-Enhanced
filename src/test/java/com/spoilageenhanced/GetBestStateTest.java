package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 220 regression test: FoodSpoilageUtil.getBestState + getBestTimestamp.
 *
 * <p>These two methods are the pair that GourdBlockMixin uses to inherit the
 * timestamp of the placed item onto the placed block. getBestState returns the
 * BEST (freshest) state present in the stack; getBestTimestamp returns the
 * max expiration of that class. The pair must agree: getBestTimestamp must
 * return a valid timestamp when getBestState returns a non-empty class.</p>
 */
public class GetBestStateTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void emptyStackReturnsFresh() {
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(ItemStack.EMPTY),
                "Empty stack must report FRESH (no spoilage info)");
    }

    @Test
    void nonSpoilableStackReturnsFresh() {
        ItemStack stack = new ItemStack(Items.STONE);
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(stack),
                "Non-spoilable stack must report FRESH");
    }

    @Test
    void stackWithoutComponentReturnsFresh() {
        ItemStack stack = new ItemStack(Items.EGG);
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(stack),
                "Stack without SPOILAGE component must report FRESH");
    }

    @Test
    void stackWithOnlyFreshReturnsFresh() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L, 2000L), List.of(), 0, 1.0));
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(stack),
                "Stack with only fresh entries must report FRESH");
    }

    @Test
    void stackWithFreshAndStaleReturnsFresh() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L), List.of(500L), 0, 1.0));
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(stack),
                "Stack with both fresh and stale must report FRESH (the best)");
    }

    @Test
    void stackWithOnlyStaleReturnsStale() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(500L), 0, 1.0));
        assertEquals(SpoilageState.STALE, FoodSpoilageUtil.getBestState(stack),
                "Stack with only stale entries must report STALE");
    }

    @Test
    void stackWithOnlyRottenReturnsRotten() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(), 3, 1.0));
        assertEquals(SpoilageState.ROTTEN, FoodSpoilageUtil.getBestState(stack),
                "Stack with only rotten entries must report ROTTEN");
    }

    @Test
    void stackWithStaleAndRottenReturnsStale() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(500L), 3, 1.0));
        assertEquals(SpoilageState.STALE, FoodSpoilageUtil.getBestState(stack),
                "Stack with stale and rotten must report STALE (the best)");
    }

    @Test
    void getBestTimestampReturnsMaxOfFreshClass() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L, 2000L, 3000L), List.of(), 0, 1.0));
        assertEquals(3000L, FoodSpoilageUtil.getBestTimestamp(stack, SpoilageState.FRESH),
                "getBestTimestamp for FRESH must return the max fresh expiration");
    }

    @Test
    void getBestTimestampReturnsMaxOfStaleClass() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(500L, 600L), 0, 1.0));
        assertEquals(600L, FoodSpoilageUtil.getBestTimestamp(stack, SpoilageState.STALE),
                "getBestTimestamp for STALE must return the max stale expiration");
    }

    @Test
    void getBestTimestampReturnsMinusOneForEmptyClass() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L), List.of(), 0, 1.0));
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack, SpoilageState.STALE),
                "getBestTimestamp for an empty class must return -1L");
    }

    @Test
    void getBestTimestampReturnsMinusOneForNullData() {
        ItemStack stack = new ItemStack(Items.EGG);
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack, SpoilageState.FRESH),
                "getBestTimestamp for a stack without SPOILAGE must return -1L");
    }
}
