package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 231 regression test: FoodSpoilageUtil.rescaleItemTimestamps.
 *
 * <p>The rescale path is called when the speed multiplier changes (config reload or
 * dynamic command). It rescales all fresh/stale expirations by the ratio of old/new
 * multiplier. Key edge cases: ratio=1.0 is a no-op, ratio=0.5 doubles durations,
 * ratio=2.0 halves them, Infinity clamps to NEVER, already-expired entries stay expired.</p>
 */
public class RescaleItemTimestampsTest {

    @Test
    void nullDataReturnsDefault() {
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(null, 1000L, 1.0, 1.0);
        assertEquals(SpoilageData.DEFAULT, result, "null data must return DEFAULT");
    }

    @Test
    void emptyDataReturnsSame() {
        SpoilageData data = SpoilageData.DEFAULT;
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 1.0, 1.0);
        assertEquals(data, result, "Empty data must return the same instance");
    }

    @Test
    void ratioOneIsNoOp() {
        SpoilageData data = new SpoilageData(
                List.of(5000L, 6000L),  // fresh
                List.of(3000L),         // stale
                1, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 1.0, 1.0);
        assertEquals(5000L, result.freshExpirations().get(0), "ratio=1.0 must preserve fresh expiration");
        assertEquals(6000L, result.freshExpirations().get(1));
        assertEquals(3000L, result.staleExpirations().get(0));
    }

    @Test
    void ratioHalfDoublesDurations() {
        // ratio=0.5 means new multiplier is half the old — durations should double.
        SpoilageData data = new SpoilageData(
                List.of(2000L),  // remaining = 1000
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 0.5, 1.0);
        // remaining * 0.5 = 500, but Math.max(1L, 500) = 500, new exp = 1000 + 500 = 1500
        // Wait — ratio=0.5 means new duration is HALF (faster spoilage), so remaining should HALF
        assertEquals(1500L, result.freshExpirations().get(0),
                "ratio=0.5 must halve the remaining duration (1000 * 0.5 = 500, exp = 1000 + 500)");
    }

    @Test
    void ratioDoubleHalvesDurations() {
        // ratio=2.0 means new multiplier is double — durations should double (slower spoilage).
        SpoilageData data = new SpoilageData(
                List.of(2000L),  // remaining = 1000
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 2.0, 1.0);
        // remaining * 2.0 = 2000, new exp = 1000 + 2000 = 3000
        assertEquals(3000L, result.freshExpirations().get(0),
                "ratio=2.0 must double the remaining duration (1000 * 2 = 2000, exp = 1000 + 2000)");
    }

    @Test
    void infinityRatioClampsToNever() {
        SpoilageData data = new SpoilageData(
                List.of(2000L),  // remaining = 1000
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Infinity ratio must clamp to NEVER (Long.MAX_VALUE)");
    }

    @Test
    void nanRatioClampsToNever() {
        SpoilageData data = new SpoilageData(
                List.of(2000L),
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, Double.NaN, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "NaN ratio must clamp to NEVER");
    }

    @Test
    void alreadyExpiredStaysExpired() {
        // Fresh entry already expired (exp < currentTime) — must stay expired.
        SpoilageData data = new SpoilageData(
                List.of(500L),  // expired (currentTime=1000)
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 0.5, 1.0);
        assertEquals(500L, result.freshExpirations().get(0),
                "Already-expired entry must stay expired (no resurrection)");
    }

    @Test
    void neverSentinelStaysNever() {
        SpoilageData data = new SpoilageData(
                List.of(Long.MAX_VALUE),  // NEVER sentinel
                List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 0.5, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "NEVER sentinel must stay NEVER");
    }

    @Test
    void newMultiplierPropagates() {
        SpoilageData data = new SpoilageData(
                List.of(2000L), List.of(), 0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 1.0, 2.5);
        assertEquals(2.5, result.speedMultiplier(), 0.0,
                "newMultiplier must propagate to the result");
    }

    @Test
    void rottenCountPreserved() {
        SpoilageData data = new SpoilageData(
                List.of(), List.of(), 5, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 1.0, 1.0);
        assertEquals(5, result.rottenCount(), "rottenCount must be preserved");
    }
}
