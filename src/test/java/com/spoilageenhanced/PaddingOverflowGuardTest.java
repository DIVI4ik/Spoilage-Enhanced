package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 233 regression test: padding overflow guard in updateSpoilageDataImpl.
 *
 * <p>The padding path adds `currentTime + freshDuration` for each missing tracker.
 * If freshDuration is very large (e.g. a future config with a huge base duration),
 * the addition can overflow to negative, making the entry look already-expired and
 * move to stale on the next tick. The fix clamps to Long.MAX_VALUE (the NEVER sentinel)
 * when the addition would overflow.</p>
 */
public class PaddingOverflowGuardTest {

    @Test
    void normalDurationPaddedCorrectly() {
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                SpoilageData.DEFAULT, 3, 1000L, () -> 5000L, () -> 3000L, 1.0);
        for (long exp : result.freshExpirations()) {
            assertEquals(6000L, exp, "Normal padding: currentTime + duration (1000 + 5000)");
        }
    }

    @Test
    void nearMaxDurationClampsToNever() {
        // freshDuration = Long.MAX_VALUE - 500, currentTime = 1000
        // currentTime + freshDuration = 1000 + (Long.MAX_VALUE - 500) overflows to 499
        // Without the guard, the entry would look expired (499 < 1000) and move to stale.
        // With the guard, it clamps to Long.MAX_VALUE.
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                SpoilageData.DEFAULT, 1, 1000L,
                () -> Long.MAX_VALUE - 500L,
                () -> 3000L, 1.0);
        assertEquals(1, result.freshExpirations().size(), "Must pad with 1 entry");
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Overflow must clamp to NEVER (Long.MAX_VALUE), not wrap to negative");
        assertEquals(0, result.staleExpirations().size(),
                "Overflow-clamped entry must NOT move to stale");
    }

    @Test
    void maxValueDurationClampsToNever() {
        // freshDuration = Long.MAX_VALUE itself — any addition overflows.
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                SpoilageData.DEFAULT, 1, 1000L,
                () -> Long.MAX_VALUE,
                () -> 3000L, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Long.MAX_VALUE duration must clamp to Long.MAX_VALUE (no overflow)");
    }

    @Test
    void freshToStaleMoveClampsHugeStaleDuration() {
        // Pass 1166 (L7 — boundary): the fresh->stale move stored exp + staleDuration.
        // With a huge staleDuration (unclamped by the config loader) the sum wrapped
        // negative and the item moved to ROTTEN on the very next tick. The fix clamps
        // the stored stale expiration to the NEVER sentinel.
        SpoilageData data = new SpoilageData(
                java.util.List.of(1000L), java.util.List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 2000L, () -> 5000L, () -> Long.MAX_VALUE - 100L, 1.0);
        assertEquals(0, result.freshExpirations().size(), "Expired entry must leave fresh");
        assertEquals(1, result.staleExpirations().size(), "Expired entry must land in stale");
        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Overflowing exp + staleDuration must clamp to NEVER, not wrap negative");
        assertEquals(0, result.rottenCount(), "Clamped entry must NOT be rotten");
    }

    @Test
    void freshToStaleMoveNormalDurationUnchanged() {
        // Control: a normal staleDuration still produces a real finite stale expiration.
        SpoilageData data = new SpoilageData(
                java.util.List.of(1000L), java.util.List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 1, 2000L, () -> 5000L, () -> 3000L, 1.0);
        assertEquals(1, result.staleExpirations().size());
        assertEquals(4000L, result.staleExpirations().get(0),
                "Normal move: exp + staleDuration (1000 + 3000)");
    }

    @Test
    void excessTrackerPaddingClampsHugeFreshDuration() {
        // Pass 1166 (L7 — boundary): the excess-tracker branch (both lists empty, stack
        // count larger than tracked) padded with currentTime + freshDuration — the same
        // unguarded addition pass 233 fixed in the main padding branch. A huge
        // freshDuration wrapped it negative and the new tracker read as already expired
        // on its first tick.
        SpoilageData data = new SpoilageData(
                java.util.List.of(), java.util.List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.updateSpoilageDataLazy(
                data, 3, 2000L, () -> Long.MAX_VALUE - 100L, () -> 3000L, 1.0);
        assertEquals(3, result.freshExpirations().size(), "Must pad 3 trackers");
        for (long exp : result.freshExpirations()) {
            assertEquals(Long.MAX_VALUE, exp,
                    "Overflowing currentTime + freshDuration must clamp to NEVER");
        }
    }
}
