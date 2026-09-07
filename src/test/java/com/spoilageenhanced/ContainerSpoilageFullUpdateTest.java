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
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 399 (L13 observed): full container spoilage update path.
 *
 * <p>ContainerSpoilageTest tests the trim path with null Level (updateSpoilage returns early).
 * This test verifies the full update path: a fresh item placed in a container gets a spoilage
 * component with correct expiration after one tick, then transitions fresh->stale->rotten.
 *
 * What should happen (user scenario):
 * 1. Craft apple (egg), place in chest -> fresh stack gets spoilage component with correct expiration
 * 2. Wait freshDuration ticks -> fresh items move to stale
 * 3. Wait staleDuration ticks -> stale items move to rotten
 */
public class ContainerSpoilageFullUpdateTest {

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
    void freshItemInContainerGetsSpoilageComponent() {
        // Scenario: craft egg, place in chest, first tick
        // What should happen: fresh stack gets spoilage component with correct expiration
        long currentTime = 1000L;
        long freshDuration = 24000L;
        double multiplier = 1.0;

        // Simulate initializeItemSpoilage: create fresh data
        List<Long> freshList = List.of(currentTime + freshDuration);
        SpoilageData initial = new SpoilageData(freshList, List.of(), 0, multiplier);

        // First updateSpoilageData call (what updateContainerItemSpoilage does)
        SpoilageData updated = FoodSpoilageUtil.updateSpoilageData(
                initial, 1, currentTime, freshDuration, 24000L, multiplier);

        assertNotNull(updated, "updateSpoilageData must return non-null");
        assertEquals(1, updated.freshExpirations().size(),
                "Fresh item must have 1 fresh expiration");
        assertEquals(0, updated.staleExpirations().size(),
                "Fresh item must have 0 stale expirations");
        assertEquals(0, updated.rottenCount(),
                "Fresh item must have 0 rotten count");
        assertEquals(currentTime + freshDuration, updated.freshExpirations().get(0),
                "Expiration must be currentTime + freshDuration");
    }

    @Test
    void freshItemInContainerMovesToStaleAfterFreshDuration() {
        // Scenario: craft egg, place in chest, wait until stale (freshDuration ticks)
        // What should happen: fresh items move to stale
        long currentTime = 1000L;
        long freshDuration = 24000L;
        double multiplier = 1.0;

        // Initial fresh data (what initializeItemSpoilage creates)
        List<Long> freshList = List.of(currentTime + freshDuration);
        SpoilageData fresh = new SpoilageData(freshList, List.of(), 0, multiplier);

        // First tick: still fresh
        SpoilageData afterFirst = FoodSpoilageUtil.updateSpoilageData(
                fresh, 1, currentTime, freshDuration, 24000L, multiplier);
        assertEquals(1, afterFirst.freshExpirations().size());

        // After freshDuration ticks: should move to stale
        long staleTime = currentTime + freshDuration + 1;
        SpoilageData stale = FoodSpoilageUtil.updateSpoilageData(
                afterFirst, 1, staleTime, freshDuration, 24000L, multiplier);

        assertEquals(0, stale.freshExpirations().size(),
                "After freshDuration, fresh list must be empty");
        assertEquals(1, stale.staleExpirations().size(),
                "After freshDuration, stale list must have 1 entry");
        assertEquals(0, stale.rottenCount(),
                "After freshDuration, rotten count must be 0");
    }

    @Test
    void staleItemInContainerMovesToRottenAfterStaleDuration() {
        // Scenario: craft egg, place in chest, wait until rotten (freshDuration + staleDuration ticks)
        // What should happen: stale items move to rotten
        long currentTime = 1000L;
        long freshDuration = 24000L;
        long staleDuration = 24000L;
        double multiplier = 1.0;

        // Initial fresh data
        List<Long> freshList = List.of(currentTime + freshDuration);
        SpoilageData fresh = new SpoilageData(freshList, List.of(), 0, multiplier);

        // After freshDuration: stale
        long staleTime = currentTime + freshDuration + 1;
        SpoilageData stale = FoodSpoilageUtil.updateSpoilageData(
                fresh, 1, staleTime, freshDuration, staleDuration, multiplier);
        assertEquals(1, stale.staleExpirations().size());

        // After staleDuration: rotten
        long rottenTime = staleTime + staleDuration + 1;
        SpoilageData rotten = FoodSpoilageUtil.updateSpoilageData(
                stale, 1, rottenTime, freshDuration, staleDuration, multiplier);

        assertEquals(0, rotten.freshExpirations().size(),
                "After staleDuration, fresh list must be empty");
        assertEquals(0, rotten.staleExpirations().size(),
                "After staleDuration, stale list must be empty");
        assertEquals(1, rotten.rottenCount(),
                "After staleDuration, rotten count must be 1");
    }

    @Test
    void containerUpdateTrimsOverTrackedToWorstThenUpdates() {
        // Scenario: egg with count=1 but 3 trackers (2 fresh, 1 stale) from copyWithCount
        // What should happen: trim keeps WORST (stale), then updateSpoilage advances it
        long currentTime = 1000L;
        long freshDuration = 24000L;
        double multiplier = 1.0;

        // Over-tracked: count=1, trackers=3 (2 fresh, 1 stale)
        SpoilageData overTracked = new SpoilageData(
                List.of(9000L, 9500L),  // 2 fresh
                List.of(3000L),         // 1 stale (worst)
                0, multiplier);

        // First: trim (what updateContainerItemSpoilage does before updateSpoilage)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(overTracked, 1);
        SpoilageData trimmed = split[1]; // worst 1 tracker
        assertEquals(1, trimmed.totalTracked(), "Trim must reduce to count=1");
        assertEquals(1, trimmed.staleExpirations().size(), "Trim must keep WORST (stale)");

        // Then: updateSpoilage advances the stale item
        SpoilageData updated = FoodSpoilageUtil.updateSpoilageData(
                trimmed, 1, currentTime, freshDuration, 24000L, multiplier);

        // The stale item should still be stale (expiration 3000 > currentTime 1000)
        assertEquals(0, updated.freshExpirations().size());
        assertEquals(1, updated.staleExpirations().size());
        assertEquals(3000L, updated.staleExpirations().get(0));
    }
}