package com.spoilageenhanced;

import com.mojang.logging.LogUtils;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1336 (L10 coverage): test the fresh/stale/rotten eat chain from
 * ItemStackMixin.onFinishUsingItem. Mutation 2 (pass 1335) replaced the fresh
 * branch with a comment so fresh food fell through to stale — the 712-test
 * suite stayed green (712/0/0). This test catches that mutation by asserting
 * the correct branch is taken for each spoilage state.
 */
class EatChainTest {

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
    void freshFoodEatenNoDebuff() {
        // A stack with fresh_expirations only — the "fresh" branch
        ItemStack stack = new ItemStack(Items.APPLE, 5);
        long future = System.currentTimeMillis() + 100000L;
        SpoilageData data = new SpoilageData(
                List.of(future, future, future, future, future),
                List.of(),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        // Extract the 1 WORST item that was just consumed (player eats oldest first)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData consumedItemData = split[1];

        // Fresh branch: freshExpirations not empty, staleExpirations empty, rottenCount == 0
        assertFalse(consumedItemData.freshExpirations().isEmpty(),
                "Consumed item must have fresh_expirations (fresh branch)");
        assertTrue(consumedItemData.staleExpirations().isEmpty(),
                "Consumed item must NOT have stale_expirations");
        assertEquals(0, consumedItemData.rottenCount(),
                "Consumed item must NOT be rotten");

        // Remaining stack should have 4 fresh entries
        assertEquals(4, split[0].freshExpirations().size(),
                "Remaining stack must have 4 fresh entries");
    }

    @Test
    void staleFoodEatenNauseaBranch() {
        // A stack with stale_expirations only — the "stale" branch
        ItemStack stack = new ItemStack(Items.APPLE, 3);
        long past = System.currentTimeMillis() - 1000L;
        SpoilageData data = new SpoilageData(
                List.of(),
                List.of(past, past, past),
                0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData consumedItemData = split[1];

        // Stale branch: freshExpirations empty, staleExpirations not empty, rottenCount == 0
        assertTrue(consumedItemData.freshExpirations().isEmpty(),
                "Consumed item must NOT have fresh_expirations");
        assertFalse(consumedItemData.staleExpirations().isEmpty(),
                "Consumed item must have stale_expirations (stale branch)");
        assertEquals(0, consumedItemData.rottenCount(),
                "Consumed item must NOT be rotten");

        // Remaining stack should have 2 stale entries
        assertEquals(2, split[0].staleExpirations().size(),
                "Remaining stack must have 2 stale entries");
    }

    @Test
    void rottenFoodEatenPoisonBranch() {
        // A stack with rotten_count only — the "rotten" branch
        ItemStack stack = new ItemStack(Items.APPLE, 2);
        SpoilageData data = new SpoilageData(
                List.of(),
                List.of(),
                2, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData consumedItemData = split[1];

        // Rotten branch: freshExpirations empty, staleExpirations empty, rottenCount > 0
        assertTrue(consumedItemData.freshExpirations().isEmpty(),
                "Consumed item must NOT have fresh_expirations");
        assertTrue(consumedItemData.staleExpirations().isEmpty(),
                "Consumed item must NOT have stale_expirations");
        assertEquals(1, consumedItemData.rottenCount(),
                "Consumed item must have rottenCount == 1 (rotten branch)");

        // Remaining stack should have 1 rotten
        assertEquals(1, split[0].rottenCount(),
                "Remaining stack must have 1 rotten");
    }

    @Test
    void mixedStackEatsWorstFirst() {
        // A stack with fresh, stale, AND rotten — must eat rotten first (worst-first)
        ItemStack stack = new ItemStack(Items.APPLE, 3);
        long future = System.currentTimeMillis() + 100000L;
        long past = System.currentTimeMillis() - 1000L;
        SpoilageData data = new SpoilageData(
                List.of(future),      // 1 fresh
                List.of(past),        // 1 stale
                1,                    // 1 rotten
                1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        // First extraction: should get the rotten one (worst-first order)
        SpoilageData[] split1 = FoodSpoilageUtil.extractWorstItems(data, 1);
        SpoilageData consumed1 = split1[1];

        assertTrue(consumed1.freshExpirations().isEmpty());
        assertTrue(consumed1.staleExpirations().isEmpty());
        assertEquals(1, consumed1.rottenCount(),
                "First eaten must be rotten (worst-first)");

        // Second extraction from remaining: should get stale
        SpoilageData[] split2 = FoodSpoilageUtil.extractWorstItems(split1[0], 1);
        SpoilageData consumed2 = split2[1];

        assertTrue(consumed2.freshExpirations().isEmpty());
        assertFalse(consumed2.staleExpirations().isEmpty());
        assertEquals(0, consumed2.rottenCount(),
                "Second eaten must be stale");

        // Third extraction: should get fresh
        SpoilageData[] split3 = FoodSpoilageUtil.extractWorstItems(split2[0], 1);
        SpoilageData consumed3 = split3[1];

        assertFalse(consumed3.freshExpirations().isEmpty());
        assertTrue(consumed3.staleExpirations().isEmpty());
        assertEquals(0, consumed3.rottenCount(),
                "Third eaten must be fresh");
    }

    @Test
    void extractWorstItemsHandlesEmptyStack() {
        // Edge case: empty data pads the extracted side with the NEVER sentinel
        // (Long.MAX_VALUE) so the invariant extracted.totalTracked() == amount holds (BUG-13).
        SpoilageData data = SpoilageData.DEFAULT;
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        assertEquals(SpoilageData.DEFAULT, split[0]);
        assertEquals(1, split[1].freshExpirations().size(),
                "Extracted must be padded with 1 NEVER-sentinel fresh entry");
        assertEquals(Long.MAX_VALUE, split[1].freshExpirations().get(0),
                "The padding entry must be the Long.MAX_VALUE NEVER sentinel");
    }

    @Test
    void extractWorstItemsHandlesZeroAmount() {
        // Edge case: amount = 0
        SpoilageData data = new SpoilageData(
                List.of(System.currentTimeMillis() + 100000L),
                List.of(),
                0, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 0);
        assertEquals(1, split[0].freshExpirations().size());
        assertEquals(0, split[1].freshExpirations().size());
    }
}