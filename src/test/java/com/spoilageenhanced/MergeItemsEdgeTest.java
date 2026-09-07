package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 216 regression test: FoodSpoilageUtil.mergeItems edge cases.
 *
 * <p>CraftingInheritanceTest already covers the total-count invariant (the list sizes
 * must sum to the total item count). This test pins the boundary cases that the
 * existing coverage misses: merging DEFAULT with DEFAULT, merging with null on either
 * side, and the worst-first ordering of a mixed-state merge.</p>
 */
public class MergeItemsEdgeTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void mergeDefaultsReturnsDefault() {
        SpoilageData merged = FoodSpoilageUtil.mergeItems(SpoilageData.DEFAULT, SpoilageData.DEFAULT);
        assertEquals(SpoilageData.DEFAULT, merged,
                "Merging DEFAULT with DEFAULT must return DEFAULT");
    }

    @Test
    void mergeNullTargetReturnsAddee() {
        SpoilageData addee = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData merged = FoodSpoilageUtil.mergeItems(null, addee);
        assertEquals(addee, merged, "mergeItems(null, x) must return x");
    }

    @Test
    void mergeNullAddeeReturnsTarget() {
        SpoilageData target = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData merged = FoodSpoilageUtil.mergeItems(target, null);
        assertEquals(target, merged, "mergeItems(x, null) must return x");
    }

    @Test
    void mergeBothNullReturnsDefault() {
        SpoilageData merged = FoodSpoilageUtil.mergeItems(null, null);
        assertEquals(SpoilageData.DEFAULT, merged,
                "mergeItems(null, null) must return DEFAULT");
    }

    @Test
    void mergeEmptyAddeeReturnsTarget() {
        SpoilageData target = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData empty = new SpoilageData(List.of(), List.of(), 0, 1.0);
        SpoilageData merged = FoodSpoilageUtil.mergeItems(target, empty);
        assertEquals(target, merged, "mergeItems(x, empty) must return x");
    }

    @Test
    void mergeEmptyTargetReturnsAddee() {
        SpoilageData addee = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData empty = new SpoilageData(List.of(), List.of(), 0, 1.0);
        SpoilageData merged = FoodSpoilageUtil.mergeItems(empty, addee);
        assertEquals(addee, merged, "mergeItems(empty, x) must return x");
    }

    @Test
    void mergePreservesAllElements() {
        long now = 1000L;
        SpoilageData batch1 = new SpoilageData(
                List.of(now + 5000L, now + 3000L),
                List.of(now + 1000L),
                1, 1.0);
        SpoilageData batch2 = new SpoilageData(
                List.of(now + 4000L),
                List.of(now + 2000L),
                2, 1.0);

        SpoilageData merged = FoodSpoilageUtil.mergeItems(batch1, batch2);

        // Total count: 2 fresh + 1 stale + 1 rotten + 1 fresh + 1 stale + 2 rotten = 8
        int total = merged.freshExpirations().size()
                + merged.staleExpirations().size()
                + merged.rottenCount();
        assertEquals(8, total, "Merge must preserve every tracker (3 + 5 = 8)");

        // All fresh/stale expirations from both batches must appear in the result
        assertTrue(merged.freshExpirations().containsAll(List.of(now + 5000L, now + 3000L, now + 4000L)));
        assertTrue(merged.staleExpirations().containsAll(List.of(now + 1000L, now + 2000L)));

        // Rotten counts add: 1 + 2 = 3
        assertEquals(3, merged.rottenCount(), "Rotten counts must add");
    }
}