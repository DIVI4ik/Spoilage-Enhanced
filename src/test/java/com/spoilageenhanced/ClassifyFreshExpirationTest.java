package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1165 regression test: overflow guard in fresh-expiration classification.
 *
 * <p>ItemClientMixin's tooltip counting loop classifies each fresh-list expiration as
 * FRESH / STALE / ROTTEN. The ROTTEN test was {@code currentTime >= exp + staleDuration}
 * with no overflow guard: staleDuration is clamped by clampHandEditedValues (pass 1168)
 * for stale <= 0, but values near Long.MAX_VALUE pass through unclamped (same finding
 * as pass 1164), so a hand-edited huge stale value made the addition wrap negative and the
 * comparison read true, counting a fresh-expired item as ROTTEN in the tooltip though its
 * stale window never ended.</p>
 *
 * <p>The classification is extracted to
 * {@link FoodSpoilageUtil#classifyFreshExpiration(long, long, long)} so this test drives
 * the real code the mixin calls (the mixin body itself touches
 * {@code Minecraft.getInstance()} and cannot run headless).</p>
 */
public class ClassifyFreshExpirationTest {

    private static final long NEVER = Long.MAX_VALUE;

    @Test
    void freshWhileCurrentTimeBeforeExpiration() {
        assertEquals(SpoilageState.FRESH,
                FoodSpoilageUtil.classifyFreshExpiration(10_000L, 5_000L, 3_000L),
                "currentTime < exp means still fresh");
    }

    @Test
    void staleInsideStaleWindow() {
        assertEquals(SpoilageState.STALE,
                FoodSpoilageUtil.classifyFreshExpiration(10_000L, 12_000L, 3_000L),
                "freshness expired but the stale window (exp..exp+staleDuration) has not");
    }

    @Test
    void rottenAfterStaleWindow() {
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyFreshExpiration(10_000L, 13_000L, 3_000L),
                "currentTime >= exp + staleDuration means rotten");
    }

    @Test
    void hugeStaleDurationDoesNotCountAsRotten() {
        // The defect: exp + staleDuration overflows to negative, and the unguarded
        // comparison currentTime >= (negative) read true -> ROTTEN. The guard must
        // answer STALE: the stale window never ends, so the item can never rot.
        long exp = 1_441_731L;
        long staleDuration = NEVER - 100L;
        assertEquals(SpoilageState.STALE,
                FoodSpoilageUtil.classifyFreshExpiration(exp, 5_000_000L, staleDuration),
                "exp + staleDuration would overflow; the item must read STALE (window "
                        + "never ends), not ROTTEN (wrapped-negative comparison)");
    }

    @Test
    void hugeStaleDurationStillFreshBeforeExpiration() {
        // The overflow must not change the FRESH verdict: an item whose freshness has
        // not expired yet is fresh regardless of what its stale window would do.
        long exp = 10_000_000L;
        long staleDuration = NEVER - 100L;
        assertEquals(SpoilageState.FRESH,
                FoodSpoilageUtil.classifyFreshExpiration(exp, 5_000_000L, staleDuration),
                "currentTime < exp must stay FRESH even when exp + staleDuration overflows");
    }

    @Test
    void exactlyAtBoundaryIsRotten() {
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyFreshExpiration(10_000L, 13_000L, 3_000L),
                "currentTime == exp + staleDuration is the first rotten tick");
    }

    @Test
    void staleDurationZeroExpiresImmediately() {
        // A zero stale window: the moment freshness expires the item is rotten.
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyFreshExpiration(10_000L, 10_000L, 0L),
                "exp + 0 == currentTime means rotten on the same tick");
    }
}
