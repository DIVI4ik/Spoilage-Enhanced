package com.spoilageenhanced;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1166 regression test: overflow guard in legacy block-entry classification.
 *
 * <p>BlockSpoilageData's two legacy-entry call sites (getSpoilageState and
 * getTicksUntilNextStage) compare the block's age against
 * {@code freshDuration + staleDuration}. The stale window test had no overflow guard:
 * staleDuration is clamped by clampHandEditedValues (pass 1168) for stale <= 0, but values near Long.MAX_VALUE pass through unclamped (same finding as pass 1164 —
 * clampHandEditedValues), so a
 * hand-edited huge stale value made the sum wrap negative and {@code age < (negative)}
 * read false, answering ROTTEN (and 0 remaining ticks) for a block still inside its
 * stale window.</p>
 *
 * <p>The classification is extracted to
 * {@link FoodSpoilageUtil#classifyLegacyAge(long, long, long)} so this test drives the
 * real code getSpoilageState calls (the call site reads the game time from a Level,
 * which a unit test cannot construct).</p>
 */
public class ClassifyLegacyAgeTest {

    private static final long NEVER = Long.MAX_VALUE;

    @Test
    void freshWhileAgeBelowFreshDuration() {
        assertEquals(SpoilageState.FRESH,
                FoodSpoilageUtil.classifyLegacyAge(100L, 200L, 300L),
                "age < freshDuration means still fresh");
    }

    @Test
    void staleInsideStaleWindow() {
        assertEquals(SpoilageState.STALE,
                FoodSpoilageUtil.classifyLegacyAge(250L, 200L, 300L),
                "freshness expired but age < freshDuration + staleDuration");
    }

    @Test
    void rottenAfterStaleWindow() {
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyLegacyAge(500L, 200L, 300L),
                "age >= freshDuration + staleDuration means rotten");
    }

    @Test
    void hugeStaleDurationDoesNotCountAsRotten() {
        // The defect: freshDuration + staleDuration overflows to negative, and the
        // unguarded comparison age < (negative) read false -> ROTTEN. The guard must
        // answer STALE: the stale window never ends, so the block can never rot.
        long freshDuration = 200L;
        long staleDuration = NEVER - 100L;
        assertEquals(SpoilageState.STALE,
                FoodSpoilageUtil.classifyLegacyAge(250L, freshDuration, staleDuration),
                "freshDuration + staleDuration would overflow; the block must read STALE "
                        + "(window never ends), not ROTTEN (wrapped-negative comparison)");
    }

    @Test
    void hugeStaleDurationStillFreshBeforeExpiry() {
        // The overflow must not change the FRESH verdict: a block whose freshness has
        // not expired yet is fresh regardless of what its stale window would do.
        long freshDuration = 10_000L;
        long staleDuration = NEVER - 100L;
        assertEquals(SpoilageState.FRESH,
                FoodSpoilageUtil.classifyLegacyAge(5_000L, freshDuration, staleDuration),
                "age < freshDuration must stay FRESH even when freshDuration + staleDuration "
                        + "overflows");
    }

    @Test
    void exactlyAtWindowEndIsRotten() {
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyLegacyAge(500L, 200L, 300L),
                "age == freshDuration + staleDuration is the first rotten tick");
    }

    @Test
    void zeroStaleWindowExpiresImmediately() {
        // A zero stale window: the moment freshness expires the block is rotten.
        assertEquals(SpoilageState.ROTTEN,
                FoodSpoilageUtil.classifyLegacyAge(200L, 200L, 0L),
                "age == freshDuration with a zero stale window means rotten on the same tick");
    }
}
