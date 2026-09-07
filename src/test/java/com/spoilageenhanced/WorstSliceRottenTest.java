package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
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
 * Pass 223 regression test: FoodSpoilageUtil.worstSliceContainsRotten.
 *
 * <p>PLAYER_REPORT §2: a right-click on a stack of 4 carrots holding 1 rotten one
 * inserts exactly the rotten carrot into a crafting slot, because the transfer moves
 * the WORST items first (extractWorstItems) while the old guard tested
 * isEntirelyRotten on the whole carried stack. The new guard tests the slice that is
 * actually about to move.</p>
 *
 * <p>Worst-first order is rotten > stale > fresh, so the worst-N slice contains a
 * rotten entry for any N >= 1 whenever the stack has at least one rotten tracker.</p>
 */
public class WorstSliceRottenTest {

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
    void emptyStackReturnsFalse() {
        assertFalse(FoodSpoilageUtil.worstSliceContainsRotten(ItemStack.EMPTY, 1),
                "Empty stack has no rotten slice");
    }

    @Test
    void stackWithoutComponentReturnsFalse() {
        ItemStack stack = new ItemStack(Items.EGG);
        assertFalse(FoodSpoilageUtil.worstSliceContainsRotten(stack, 1),
                "Stack without SPOILAGE component has no rotten slice");
    }

    @Test
    void zeroCountReturnsFalse() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(), 3, 1.0));
        assertFalse(FoodSpoilageUtil.worstSliceContainsRotten(stack, 0),
                "N=0 asks about an empty slice — nothing moves, nothing to reject");
    }

    @Test
    void oneRottenOutOfFourRejectsRightClick() {
        // The exact scenario from the player report: 4 carrots, 1 rotten.
        // A right-click (N=1) takes the worst item — the rotten carrot.
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L, 2000L, 3000L), List.of(), 1, 1.0));
        assertTrue(FoodSpoilageUtil.worstSliceContainsRotten(stack, 1),
                "Right-click (N=1) on a stack with 1 rotten must be rejected");
    }

    @Test
    void oneRottenOutOfFourRejectsLeftClick() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L, 2000L, 3000L), List.of(), 1, 1.0));
        assertTrue(FoodSpoilageUtil.worstSliceContainsRotten(stack, 4),
                "Left-click (N=whole stack) on a stack with 1 rotten must be rejected");
    }

    @Test
    void noRottenOnlyStaleAllowsInsert() {
        // Stale items are allowed in crafting (only rotten is forbidden).
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L), List.of(500L, 600L), 0, 1.0));
        assertFalse(FoodSpoilageUtil.worstSliceContainsRotten(stack, 1),
                "A stack with stale but no rotten must be allowed");
    }

    @Test
    void onlyFreshAllowsInsert() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(1000L, 2000L), List.of(), 0, 1.0));
        assertFalse(FoodSpoilageUtil.worstSliceContainsRotten(stack, 2),
                "A fresh-only stack must be allowed");
    }

    @Test
    void entirelyRottenStackRejectsAnyCount() {
        ItemStack stack = new ItemStack(Items.EGG);
        stack.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(), 4, 1.0));
        assertTrue(FoodSpoilageUtil.worstSliceContainsRotten(stack, 1),
                "An entirely-rotten stack must be rejected for N=1");
        assertTrue(FoodSpoilageUtil.worstSliceContainsRotten(stack, 4),
                "An entirely-rotten stack must be rejected for N=4");
    }
}
