package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 287 regression test: SpoilageData accessors.
 *
 * <p>Pins the totalTracked, isEmpty, and speedMultiplier accessors. These are called
 * from the inventory tick and the HUD render path.</p>
 */
public class SpoilageDataAccessorsTest {

    @Test
    void emptyDataHasZeroTracked() {
        SpoilageData data = SpoilageData.DEFAULT;
        assertEquals(0, data.totalTracked(), "Empty data must have 0 total tracked");
        assertTrue(data.isEmpty(), "Empty data must be empty");
    }

    @Test
    void freshOnlyTrackedCount() {
        SpoilageData data = new SpoilageData(List.of(1000L, 2000L, 3000L), List.of(), 0, 1.0);
        assertEquals(3, data.totalTracked(), "3 fresh must be 3 total");
        assertFalse(data.isEmpty(), "Non-empty data must not be empty");
    }

    @Test
    void staleOnlyTrackedCount() {
        SpoilageData data = new SpoilageData(List.of(), List.of(500L, 600L), 0, 1.0);
        assertEquals(2, data.totalTracked(), "2 stale must be 2 total");
    }

    @Test
    void rottenOnlyTrackedCount() {
        SpoilageData data = new SpoilageData(List.of(), List.of(), 5, 1.0);
        assertEquals(5, data.totalTracked(), "5 rotten must be 5 total");
    }

    @Test
    void mixedTrackedCountIsSum() {
        SpoilageData data = new SpoilageData(
                List.of(1000L, 2000L),  // 2 fresh
                List.of(500L),           // 1 stale
                3,                       // 3 rotten
                1.0);
        assertEquals(6, data.totalTracked(), "2+1+3 must be 6 total");
    }

    @Test
    void speedMultiplierDefault() {
        assertEquals(1.0, SpoilageData.DEFAULT.speedMultiplier(), 0.0,
                "Default speedMultiplier must be 1.0");
    }

    @Test
    void speedMultiplierCustom() {
        SpoilageData data = new SpoilageData(List.of(), List.of(), 0, 2.5);
        assertEquals(2.5, data.speedMultiplier(), 0.0,
                "Custom speedMultiplier must be retrievable");
    }

    @Test
    void negativeRottenCountIsClampedToZero() {
        // Pass 459 (L7 — boundaries): the codec reads rotten_count as a raw int with no
        // validation, so a corrupt or hand-edited save can carry a negative value through
        // into totalTracked() — and extractWorstItems would then copy that negative count
        // into a freshly built target, surfacing as a negative count in the tooltip.
        // The compact constructor clamps at construction; this test pins that.
        SpoilageData data = new SpoilageData(List.of(1000L), List.of(), -5, 1.0);
        assertEquals(0, data.rottenCount(),
                "a negative rottenCount must be clamped to 0 at construction");
        assertEquals(1, data.totalTracked(),
                "totalTracked must count only the real entries after clamping");
    }
}
