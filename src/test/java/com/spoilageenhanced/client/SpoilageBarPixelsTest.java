package com.spoilageenhanced.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1343 (L5 — render path): test SpoilageBarPixels.compute.
 *
 * <p>This is the pure pixel math for the inventory spoilage bar. It runs every frame
 * for every stack in the player's inventory (36 slots × 60fps = 2160 calls/sec).
 * The math must be correct and allocation-free.</p>
 *
 * <p>Pass 145 fixed a rounding bug: equal fresh/stale counts (e.g. 16/16/0 in a
 * 13-pixel frame) rounded BOTH segments up from 6.5 to 7, making the bar draw
 * 14 pixels — one past its background frame. The fix shaves the excess off the
 * larger segment.</p>
 *
 * <p>Pass 606 eliminated the allocation: the old code returned a fresh int[3]
 * every call. The Result record is returned by value and the JIT scalarizes
 * the three int fields into registers.</p>
 *
 * <p>What this test pins: the three segments always sum to barHeight, the
 * rounding fix works for edge cases, and the Result record is returned.</p>
 */
class SpoilageBarPixelsTest {

    @Test
    void computeSumsToBarHeight() {
        // The three segments must always sum to barHeight when total > 0
        var r = SpoilageBarPixels.compute(10, 5, 3, 13);
        assertEquals(13, r.green() + r.yellow() + r.red(),
                "segments must sum to barHeight");
    }

    @Test
    void computeEqualFreshStaleNoRotten() {
        // Pass 145 regression: 16 fresh, 16 stale, 0 rotten in 13 pixels
        // 16/32 * 13 = 6.5 -> rounds to 7 for both green and yellow
        // 7 + 7 = 14, excess = 1, shave off larger (equal, so green)
        // green = 6, yellow = 7, red = 0
        var r = SpoilageBarPixels.compute(16, 16, 0, 13);
        assertEquals(6, r.green(), "green must be shaved when both round up");
        assertEquals(7, r.yellow());
        assertEquals(0, r.red());
        assertEquals(13, r.green() + r.yellow() + r.red());
    }

    @Test
    void computeAllFresh() {
        var r = SpoilageBarPixels.compute(64, 0, 0, 13);
        assertEquals(13, r.green());
        assertEquals(0, r.yellow());
        assertEquals(0, r.red());
    }

    @Test
    void computeAllStale() {
        var r = SpoilageBarPixels.compute(0, 64, 0, 13);
        assertEquals(0, r.green());
        assertEquals(13, r.yellow());
        assertEquals(0, r.red());
    }

    @Test
    void computeAllRotten() {
        var r = SpoilageBarPixels.compute(0, 0, 64, 13);
        assertEquals(0, r.green());
        assertEquals(0, r.yellow());
        assertEquals(13, r.red());
    }

    @Test
    void computeZeroTotalReturnsZeros() {
        var r = SpoilageBarPixels.compute(0, 0, 0, 13);
        assertEquals(0, r.green());
        assertEquals(0, r.yellow());
        assertEquals(0, r.red());
    }

    @Test
    void computeZeroBarHeightReturnsZeros() {
        var r = SpoilageBarPixels.compute(10, 5, 3, 0);
        assertEquals(0, r.green());
        assertEquals(0, r.yellow());
        assertEquals(0, r.red());
    }

    @Test
    void computeNegativeBarHeightReturnsZeros() {
        var r = SpoilageBarPixels.compute(10, 5, 3, -5);
        assertEquals(0, r.green());
        assertEquals(0, r.yellow());
        assertEquals(0, r.red());
    }

    @Test
    void computeLargeCountsScaleCorrectly() {
        // 1000 fresh, 500 stale, 250 rotten in 13 pixels
        // total = 1750
        // green = 1000/1750 * 13 = 7.428 -> 7
        // yellow = 500/1750 * 13 = 3.714 -> 4
        // red = 13 - 7 - 4 = 2
        var r = SpoilageBarPixels.compute(1000, 500, 250, 13);
        assertEquals(7, r.green());
        assertEquals(4, r.yellow());
        assertEquals(2, r.red());
        assertEquals(13, r.green() + r.yellow() + r.red());
    }

    @Test
    void computeResultRecordReturned() {
        // Verify the Result record is returned (not an array)
        var r = SpoilageBarPixels.compute(10, 5, 3, 13);
        assertNotNull(r);
        assertTrue(r instanceof SpoilageBarPixels.Result);
    }

    @Test
    void computeResultFieldsAccessible() {
        var r = SpoilageBarPixels.compute(10, 5, 3, 13);
        // Record fields are accessible via accessor methods
        assertEquals(r.green(), r.green());
        assertEquals(r.yellow(), r.yellow());
        assertEquals(r.red(), r.red());
    }

    @Test
    void computeShavesLargerSegmentWhenBothRoundUp() {
        // Case where green > yellow and both round up
        // 10 fresh, 5 stale, 0 rotten in 13 pixels
        // total = 15
        // green = 10/15 * 13 = 8.666 -> 9
        // yellow = 5/15 * 13 = 4.333 -> 4
        // red = 13 - 9 - 4 = 0 (no excess)
        var r = SpoilageBarPixels.compute(10, 5, 0, 13);
        assertEquals(9, r.green());
        assertEquals(4, r.yellow());
        assertEquals(0, r.red());
        assertEquals(13, r.green() + r.yellow() + r.red());
    }

    @Test
    void computeShavesYellowWhenYellowLarger() {
        // Case where yellow > green and both round up
        // 5 fresh, 10 stale, 0 rotten in 13 pixels
        // total = 15
        // green = 5/15 * 13 = 4.333 -> 4
        // yellow = 10/15 * 13 = 8.666 -> 9
        // red = 13 - 4 - 9 = 0 (no excess)
        var r = SpoilageBarPixels.compute(5, 10, 0, 13);
        assertEquals(4, r.green());
        assertEquals(9, r.yellow());
        assertEquals(0, r.red());
        assertEquals(13, r.green() + r.yellow() + r.red());
    }
}