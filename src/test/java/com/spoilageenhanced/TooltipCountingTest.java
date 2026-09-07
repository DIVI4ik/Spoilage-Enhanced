package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
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
 * Pass 401 (L13 observed): tooltip counting logic — what the player reads.
 *
 * <p>Scenario: hold a stack with mixed fresh/stale/rotten items, hover the tooltip.
 * The tooltip shows "X fresh / Y stale / Z rotten" counts. This test pins the
 * counting logic that produces those numbers, replicating the exact loop from
 * ItemClientMixin.onGetTooltip (lines 84-110).
 *
 * What should happen:
 * - fresh expirations in the future -> f
 * - fresh expirations past + staleDuration -> r (skipped stale, went rotten)
 * - fresh expirations past but within staleDuration -> s
 * - stale expirations in the future -> s
 * - stale expirations past -> r
 * - rottenCount -> r
 * - untracked items (count > f+s+r) -> f (assumed fresh)
 */
public class TooltipCountingTest {

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

    /** Replicates the counting loop from ItemClientMixin.onGetTooltip lines 84-110. */
    private static int[] countStates(SpoilageData data, int stackCount, long currentTime, long staleDuration) {
        int f = 0, s = 0, r = data.rottenCount();
        for (long exp : data.freshExpirations()) {
            if (exp >= Long.MAX_VALUE) {
                f++;
            } else if (currentTime >= exp + staleDuration) {
                r++;
            } else if (currentTime >= exp) {
                s++;
            } else {
                f++;
            }
        }
        for (long exp : data.staleExpirations()) {
            if (exp >= Long.MAX_VALUE) {
                s++;
            } else if (currentTime >= exp) {
                r++;
            } else {
                s++;
            }
        }
        if (stackCount > f + s + r) {
            f = stackCount - s - r;
        }
        return new int[]{f, s, r};
    }

    @Test
    void allFreshStackShowsFreshOnly() {
        // Scenario: fresh stack, tooltip shows "Fresh"
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = new SpoilageData(List.of(now + 5000L), List.of(), 0, 1.0);
        int[] counts = countStates(data, 1, now, staleDuration);
        assertEquals(1, counts[0], "fresh count");
        assertEquals(0, counts[1], "stale count");
        assertEquals(0, counts[2], "rotten count");
    }

    @Test
    void allStaleStackShowsStaleWithCount() {
        // Scenario: stale stack, tooltip shows "X stale"
        long now = 1000L;
        long staleDuration = 24000L;
        // Fresh expiration in the past but within staleDuration -> stale
        SpoilageData data = new SpoilageData(List.of(now - 100L), List.of(), 0, 1.0);
        int[] counts = countStates(data, 1, now, staleDuration);
        assertEquals(0, counts[0], "fresh count");
        assertEquals(1, counts[1], "stale count");
        assertEquals(0, counts[2], "rotten count");
    }

    @Test
    void allRottenStackShowsRottenWithCount() {
        // Scenario: rotten stack, tooltip shows "X rotten"
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = new SpoilageData(List.of(), List.of(), 3, 1.0);
        int[] counts = countStates(data, 3, now, staleDuration);
        assertEquals(0, counts[0], "fresh count");
        assertEquals(0, counts[1], "stale count");
        assertEquals(3, counts[2], "rotten count");
    }

    @Test
    void mixedStackShowsAllCounts() {
        // Scenario: mixed stack (1 fresh, 1 stale, 1 rotten), tooltip shows all three counts
        long now = 1000L;
        long staleDuration = 24000L;
        // 1 fresh (future), 1 stale (past within staleDuration), 1 rotten (rottenCount)
        SpoilageData data = new SpoilageData(List.of(now + 5000L, now - 100L), List.of(), 1, 1.0);
        int[] counts = countStates(data, 3, now, staleDuration);
        assertEquals(1, counts[0], "fresh count");
        assertEquals(1, counts[1], "stale count");
        assertEquals(1, counts[2], "rotten count");
    }

    @Test
    void freshExpiredPastStaleDurationCountsAsRotten() {
        // Scenario: fresh expiration so old it skipped stale entirely -> rotten
        long now = 1000L;
        long staleDuration = 24000L;
        // Expiration 30000 ticks ago, staleDuration 24000 -> past stale window -> rotten
        SpoilageData data = new SpoilageData(List.of(now - 30000L), List.of(), 0, 1.0);
        int[] counts = countStates(data, 1, now, staleDuration);
        assertEquals(0, counts[0], "fresh count");
        assertEquals(0, counts[1], "stale count");
        assertEquals(1, counts[2], "rotten count");
    }

    @Test
    void staleExpiredCountsAsRotten() {
        // Scenario: stale expiration in the past -> rotten
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = new SpoilageData(List.of(), List.of(now - 100L), 0, 1.0);
        int[] counts = countStates(data, 1, now, staleDuration);
        assertEquals(0, counts[0], "fresh count");
        assertEquals(0, counts[1], "stale count");
        assertEquals(1, counts[2], "rotten count");
    }

    @Test
    void untrackedItemsAssumedFresh() {
        // Scenario: stack count 5 but only 2 tracked -> 3 assumed fresh
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = new SpoilageData(List.of(now + 5000L), List.of(), 1, 1.0);
        int[] counts = countStates(data, 5, now, staleDuration);
        // f=1 (tracked fresh), s=0, r=1 (rottenCount) -> untracked = 5-0-1 = 4
        assertEquals(4, counts[0], "fresh count (1 tracked + 3 untracked)");
        assertEquals(0, counts[1], "stale count");
        assertEquals(1, counts[2], "rotten count");
    }

    @Test
    void neverExpiresSentinelCountsAsFresh() {
        // Scenario: Long.MAX_VALUE sentinel (never expires) -> fresh
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = new SpoilageData(List.of(Long.MAX_VALUE), List.of(), 0, 1.0);
        int[] counts = countStates(data, 1, now, staleDuration);
        assertEquals(1, counts[0], "fresh count");
        assertEquals(0, counts[1], "stale count");
        assertEquals(0, counts[2], "rotten count");
    }
}