package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 229 regression test: FoodSpoilageUtil.updateSpoilageDataLazy.
 *
 * <p>The lazy update path is the core state-transition logic: it moves fresh entries to
 * stale when they expire, moves stale entries to rotten when they expire, and pads/trims
 * the tracker lists to match the actual stack count. It is called from the inventory
 * tick (every 20 ticks per stack) and from the crafting/hopper transfer paths.</p>
 */
public class UpdateSpoilageDataLazyTest {

    @Test
    void emptyDataReturnsEmptyForZeroCount() {
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                SpoilageData.DEFAULT, 0, 1000L, () -> 5000L, () -> 3000L, 1.0);
        // No data, zero count — no fresh entries to pad.
        assertTrue(result.freshExpirations().isEmpty(), "No fresh entries for zero count");
        assertTrue(result.staleExpirations().isEmpty(), "No stale entries");
        assertEquals(0, result.rottenCount(), "No rotten count");
    }

    @Test
    void emptyDataPadsFreshForPositiveCount() {
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                SpoilageData.DEFAULT, 3, 1000L, () -> 5000L, () -> 3000L, 1.0);
        assertEquals(3, result.freshExpirations().size(),
                "Must pad with 3 fresh entries for count=3");
        for (long exp : result.freshExpirations()) {
            assertEquals(6000L, exp, "Each fresh entry must be currentTime + freshDuration (1000 + 5000)");
        }
    }

    @Test
    void countMismatchTrimsExcessViaWorstFirst() {
        // 3 trackers but count=1 — must trim 2 worst, keeping the best.
        SpoilageData data = new SpoilageData(
                List.of(1000L, 2000L, 3000L),  // 3 fresh
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 500L, () -> 5000L, () -> 3000L, 1.0);
        assertEquals(1, result.freshExpirations().size(),
                "Must trim to count=1");
        assertEquals(3000L, result.freshExpirations().get(0),
                "Must keep the BEST (highest expiration) after trim");
    }

    @Test
    void countMismatchPadsMissingWithFresh() {
        // 1 tracker but count=3 — must pad with 2 fresh entries.
        SpoilageData data = new SpoilageData(
                List.of(2000L),  // 1 fresh
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 3, 1000L, () -> 5000L, () -> 3000L, 1.0);
        assertEquals(3, result.freshExpirations().size(),
                "Must pad to count=3");
    }

    @Test
    void expiredFreshMovesToStale() {
        // Fresh entry already expired — must move to stale.
        SpoilageData data = new SpoilageData(
                List.of(500L),  // expired (currentTime=1000)
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 1000L, () -> 5000L, () -> 3000L, 1.0);
        assertTrue(result.freshExpirations().isEmpty(),
                "Expired fresh must move out of fresh list");
        assertEquals(1, result.staleExpirations().size(),
                "Expired fresh must move to stale list");
        assertEquals(500L + 3000L, result.staleExpirations().get(0),
                "Stale expiration must be originalExp + staleDuration (500 + 3000)");
    }

    @Test
    void expiredStaleMovesToRotten() {
        // Stale entry already expired — must move to rotten.
        SpoilageData data = new SpoilageData(
                List.of(),  // no fresh
                List.of(500L),  // expired stale
                0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 1000L, () -> 5000L, () -> 3000L, 1.0);
        assertTrue(result.staleExpirations().isEmpty(),
                "Expired stale must move out of stale list");
        assertEquals(1, result.rottenCount(),
                "Expired stale must increment rotten count");
    }

    @Test
    void supplierNotCalledWhenNoExpiration() {
        // No fresh/stale entries to expire — suppliers must not be called.
        SpoilageData data = new SpoilageData(
                List.of(5000L),  // not expired
                List.of(3000L),  // not expired
                0, 1.0);
        int[] freshCalls = {0};
        int[] staleCalls = {0};
        FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 1000L,
                () -> { freshCalls[0]++; return 5000L; },
                () -> { staleCalls[0]++; return 3000L; },
                1.0);
        assertEquals(0, freshCalls[0], "Fresh supplier must not be called when no fresh entry expires");
        assertEquals(0, staleCalls[0], "Stale supplier must not be called when no stale entry expires");
    }

    @Test
    void speedMultiplierPropagatesToResult() {
        SpoilageData data = new SpoilageData(
                List.of(2000L),  // 1 fresh
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 1000L, () -> 5000L, () -> 3000L, 2.5);
        assertEquals(2.5, result.speedMultiplier(), 0.0,
                "speedMultiplier must propagate to the result");
    }
}
