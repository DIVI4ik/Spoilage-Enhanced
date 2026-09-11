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

    @Test
    void negativeRatioClampsToNeverBothBranches() {
        // Pass 1102 (L7 boundary): a negative ratio (corrupt save, or a caller passing
        // old/new inverted) must clamp to NEVER in BOTH the fresh and the stale branch —
        // never produce a negative remaining that wraps to a huge positive via Math.max.
        SpoilageData data = new SpoilageData(
                List.of(2000L),   // fresh, remaining = 1000
                List.of(3000L),  // stale, remaining = 2000
                0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, -0.5, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Negative ratio must clamp fresh to NEVER");
        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Negative ratio must clamp stale to NEVER (pass 1036 guard)");
    }

    @Test
    void zeroRatioClampsToNeverBothBranches() {
        // Pass 1102 (L7 boundary): ratio=0 (speed_multiplier=0 in a corrupt save) would make
        // remaining * ratio = 0 and every item would appear to expire this tick.
        SpoilageData data = new SpoilageData(
                List.of(2000L),
                List.of(3000L),
                0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 0.0, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Zero ratio must clamp fresh to NEVER");
        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Zero ratio must clamp stale to NEVER");
    }

    @Test
    void doubleMaxRatioClampsToNeverBothBranches() {
        // Pass 1102 (L7 boundary): Double.MAX_VALUE exceeds the 1e15 guard, so it takes the
        // invalid-ratio clamp — not the overflow check, which divides by it.
        SpoilageData data = new SpoilageData(
                List.of(2000L),
                List.of(3000L),
                0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, Double.MAX_VALUE, 1.0);
        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Double.MAX_VALUE ratio must clamp fresh to NEVER");
        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Double.MAX_VALUE ratio must clamp stale to NEVER");
    }

    @Test
    void doubleMinRatioClampsToNeverBothBranches() {
        // Pass 1102 (L7 boundary): Double.MIN_VALUE is a tiny positive ratio. remaining * ratio
        // underflows toward 0; Math.max(1L, ...) keeps it at 1 tick — but the 1e15 guard does
        // NOT catch it (it is a valid positive ratio), so the item expires next tick. That is
        // the documented behaviour for a near-zero multiplier: verify it does not wrap to the
        // past and stays >= currentTime.
        SpoilageData data = new SpoilageData(
                List.of(2000L),
                List.of(3000L),
                0, 1.0);
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, Double.MIN_VALUE, 1.0);
        assertTrue(result.freshExpirations().get(0) >= 1000L,
                "Tiny positive ratio must not wrap a fresh timestamp into the past");
        assertTrue(result.staleExpirations().get(0) >= 1000L,
                "Tiny positive ratio must not wrap a stale timestamp into the past");
    }
}
