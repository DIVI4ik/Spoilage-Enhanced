package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1207 (L7 — boundary): pins what extractWorstItems returns on the degenerate
 * inputs ItemStackMixin.onFinishUsingItem can feed it, and that every returned slice
 * matches exactly one branch of the eat-effect chain (fresh / stale / rotten).
 *
 * <p>The concern (task, player report §12 shape): if any input produces a consumed
 * slice that matches NO branch, eating that item silently applies nothing. The
 * analysis found no such input — the padding sentinel guarantees
 * {@code freshExpirations} is non-empty whenever {@code amount > 0}, and the
 * null/amount&lt;=0 path returns DEFAULT, whose empty lists and zero rottenCount
 * land in the fresh branch (a no-op, correct for untracked food). This test pins
 * each case so a future change to the sentinel or the guards cannot silently
 * reopen the gap.</p>
 */
public class ExtractWorstItemsDegenerateTest {

    /** Case 1: null data — the method returns DEFAULT for both halves. */
    @Test
    void nullDataReturnsDefaults() {
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(null, 1);
        assertNotNull(split[0]);
        assertEquals(SpoilageData.DEFAULT, split[0]);
        assertEquals(SpoilageData.DEFAULT, split[1]);
        // The consumed slice matches the FRESH branch (all empty, rottenCount 0):
        // eating applies nothing, which is correct for an untracked item.
        assertTrue(split[1].freshExpirations().isEmpty());
        assertTrue(split[1].staleExpirations().isEmpty());
        assertEquals(0, split[1].rottenCount());
    }

    /** Case 2: empty data (present but no trackers) — the sentinel pads fresh. */
    @Test
    void emptyDataPadsWithSentinelSoFreshBranchFires() {
        SpoilageData data = new SpoilageData(List.of(), List.of(), 0, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);

        // The consumed slice must be non-empty in fresh so the eat chain's
        // fresh branch (no debuff) fires rather than falling through all branches.
        assertEquals(1, split[1].freshExpirations().size());
        assertEquals(Long.MAX_VALUE, split[1].freshExpirations().get(0));
        assertTrue(split[1].staleExpirations().isEmpty());
        assertEquals(0, split[1].rottenCount());
        // The remaining half is unchanged (still empty).
        assertEquals(0, split[0].totalTracked());
    }

    /**
     * Case 3: rottenCount exceeds the requested amount — the worst item is rotten,
     * so the ROTTEN branch (poison) fires, not the fresh branch.
     */
    @Test
    void overTrackedRottenExtractsRottenSlice() {
        SpoilageData data = new SpoilageData(List.of(), List.of(), 5, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);

        assertEquals(0, split[1].freshExpirations().size());
        assertTrue(split[1].staleExpirations().isEmpty());
        assertEquals(1, split[1].rottenCount());
        // Remaining keeps the other four rotten.
        assertEquals(4, split[0].rottenCount());
    }

    /**
     * Case 4: mixed data — rotten drains first, then stale, then fresh, so the
     * consumed slice always carries the worst class present.
     */
    @Test
    void mixedDataExtractsWorstClassFirst() {
        SpoilageData data = new SpoilageData(
                List.of(500L, 600L),   // fresh
                List.of(200L, 300L),   // stale
                2,                      // rotten
                1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);

        // Worst-first: the single consumed item is rotten.
        assertEquals(1, split[1].rottenCount());
        assertEquals(0, split[1].freshExpirations().size());
        assertEquals(0, split[1].staleExpirations().size());
        // Remaining: 1 rotten, 2 stale, 2 fresh.
        assertEquals(1, split[0].rottenCount());
        assertEquals(2, split[0].staleExpirations().size());
        assertEquals(2, split[0].freshExpirations().size());
    }

    /**
     * Case 5: amount 0 — nothing consumed; both halves preserve the source.
     * The eat chain never calls with 0, but the method is public.
     */
    @Test
    void zeroAmountConsumesNothing() {
        SpoilageData data = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 0);

        assertEquals(0, split[1].totalTracked());
        assertEquals(3, split[0].totalTracked());
    }
}
