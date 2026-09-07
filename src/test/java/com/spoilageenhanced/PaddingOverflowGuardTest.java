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
}
