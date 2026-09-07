package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 120 regression test: the exact scenario that would have thrown
 * UnsupportedOperationException before the fix —
 *
 * 1. A stack with MORE trackers than count (excess-tracker branch runs extractWorstItems)
 * 2. The extraction drains the ENTIRE stale side (split[0].staleExpirations() becomes the
 *    shared immutable empty list via the Pass 88 constructor fast path)
 * 3. A fresh item expires in the same tick (the fresh->stale transition tries to add() to
 *    that immutable empty list)
 *
 * The old identity guard (data.staleExpirations() == staleList) missed this because after the
 * excess-tracker branch, staleList comes from split[0] — a DIFFERENT record than data.
 */
public class ExcessTrackerDrainTest {

    @Test
    void excessTrackerDrainThenFreshExpiryDoesNotThrow() {
        // 3 fresh trackers (one already expired), 1 stale tracker, count = 1 -> 3 excess.
        // currentTime = 5000: fresh [1000] is expired, [9000]/[12000] are not.
        // Extraction of 3 worst: rotten(0) -> stale [2000] (drained, split[0].stale = EMPTY
        // IMMUTABLE) -> fresh [1000] (expired one). split[0].fresh = [9000, 12000].
        // Then the fresh->stale pass: no fresh item is expired at 5000... so also build the
        // mirror case where one IS.
        SpoilageData data = new SpoilageData(List.of(1000L, 9000L, 12000L), List.of(2000L), 0, 1.0);

        // count=1, currentTime=5000: excess = 4-1 = 3.
        // extractWorstItems(3): stale [2000] drained first (worst), then fresh [1000] (next
        // worst). split[0] = fresh [9000, 12000], stale = EMPTY (immutable via Pass 88).
        // Fresh->stale pass at 5000: neither 9000 nor 12000 is expired -> no add() -> safe.
        SpoilageData result = FoodSpoilageUtil.updateSpoilageData(data, 1, 5000L, 24000L, 24000L, 1.0);
        assertNotNull(result);
        // The exact split order is covered by CraftingInheritanceTest; this test asserts only
        // the no-throw guarantee and the count invariant.
        assertEquals(1, result.totalTracked());
    }

    @Test
    void excessTrackerDrainThenFreshExpiryMutatesSafely() {
        // The crash case: after the excess extraction drains the stale side to the immutable
        // empty list, a fresh item ALSO expires in the same tick — the transition must add()
        // to that list without throwing.
        // fresh [1000, 4000, 9000]: at currentTime=5000, both 1000 and 4000 are expired.
        // count=1 -> excess = 4-1 = 3. extractWorstItems(3): stale [2000] drained, then
        // fresh [1000], then fresh [4000]. split[0] = fresh [9000], stale = EMPTY IMMUTABLE.
        // Fresh->stale pass: 9000 not expired at 5000 -> no add... need the expired one to
        // SURVIVE the extraction. Excess extraction takes the WORST (lowest) first, so the
        // expired ones go first. To leave an expired fresh item in split[0], the excess must
        // run out before consuming it: excess=2 takes stale [2000] + fresh [1000], leaving
        // fresh [4000, 9000] where 4000 < currentTime=5000 IS expired.
        SpoilageData data = new SpoilageData(List.of(1000L, 4000L, 9000L), List.of(2000L), 0, 1.0);

        // count=2 -> totalTracked=4 -> excess=2.
        SpoilageData result = FoodSpoilageUtil.updateSpoilageData(data, 2, 5000L, 24000L, 24000L, 1.0);
        assertNotNull(result);
        // 4000 expired -> moved to stale (with staleDuration 24000 -> 29000); 9000 survives.
        assertEquals(1, result.freshExpirations().size());
        assertEquals(1, result.staleExpirations().size());
        // The expired fresh item (4000) moves to stale with exp + staleDuration = 28000.
        assertEquals(28000L, result.staleExpirations().get(0));
    }
}
