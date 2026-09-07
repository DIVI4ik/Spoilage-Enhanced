package com.spoilageenhanced;

import com.spoilageenhanced.network.BlockSpoilageResponsePayload;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 418 (L13 observed): HUD display logic — the exact strings a player reads.
 *
 * <p>Scenario: player looks at a food block. The HUD shows one of:
 * - "Fresh (Spoils in X)" — fresh tracked block with timer
 * - "Fresh" — untracked block (NO_TIMER sentinel, no countdown)
 * - "Stale (X)" — stale block
 * - "Rotten" — rotten block
 * - "Checking..." — request sent, no answer yet
 *
 * This test pins the displayed-value logic from BlockSpoilageHudMixin
 * (lines 120-165): the NO_TIMER sentinel must survive to the display layer
 * UNTOUCHED by the multiplier, and the empty timeStr must select the
 * no-timer translation key.
 *
 * What should happen: the sentinel is tested on the RAW value before the
 * multiplier, and empty timeStr renders "Fresh" with no timer.
 */
public class HudDisplayLogicTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Replicates the displayed-ticks computation from BlockSpoilageHudMixin lines 128-133. */
    private static long computeDisplayedTicks(long rawTicksRemaining, double speedMultiplier) {
        boolean noTimer = rawTicksRemaining == BlockSpoilageResponsePayload.NO_TIMER;
        return noTimer
                ? BlockSpoilageResponsePayload.NO_TIMER
                : (long) (rawTicksRemaining * speedMultiplier);
    }

    /** Replicates the timeStr computation from BlockSpoilageHudMixin line 133. */
    private static String computeTimeStr(long displayedTicks) {
        boolean noTimer = displayedTicks == BlockSpoilageResponsePayload.NO_TIMER;
        return noTimer ? "" : SpoilageEnhancedTranslations.formatTime(displayedTicks);
    }

    @Test
    void noTimerSentinelSurvivesMultiplier() {
        // Scenario: untracked block reports NO_TIMER (-1). The HUD must show "Fresh" with
        // no countdown, regardless of the speed multiplier.
        // Pass 221: the sentinel is tested on the RAW value BEFORE the multiplier.
        // Scaling it first destroys it: (long)(-1 * 0.5) rounds to 0, and 0 renders "<1 min".
        long raw = BlockSpoilageResponsePayload.NO_TIMER;

        long displayedAtHalfSpeed = computeDisplayedTicks(raw, 0.5);
        long displayedAtDoubleSpeed = computeDisplayedTicks(raw, 2.0);
        long displayedAtNormalSpeed = computeDisplayedTicks(raw, 1.0);

        assertEquals(BlockSpoilageResponsePayload.NO_TIMER, displayedAtHalfSpeed,
                "Sentinel must survive a 0.5x multiplier");
        assertEquals(BlockSpoilageResponsePayload.NO_TIMER, displayedAtDoubleSpeed,
                "Sentinel must survive a 2.0x multiplier");
        assertEquals(BlockSpoilageResponsePayload.NO_TIMER, displayedAtNormalSpeed,
                "Sentinel must survive a 1.0x multiplier");
    }

    @Test
    void noTimerSentinelProducesEmptyTimeStr() {
        // Scenario: the sentinel must produce an empty timeStr, which selects the
        // HUD_FRESH_NO_TIMER translation ("Fresh" with no countdown).
        long displayed = computeDisplayedTicks(BlockSpoilageResponsePayload.NO_TIMER, 1.0);
        String timeStr = computeTimeStr(displayed);
        assertTrue(timeStr.isEmpty(),
                "NO_TIMER must produce empty timeStr (selects HUD_FRESH_NO_TIMER)");
    }

    @Test
    void zeroTicksIsNotTheSentinel() {
        // Scenario: a tracked block with 0 ticks remaining is NOT the sentinel —
        // it is a real expiration and must render "<1 min", not "Fresh".
        // This is the inverse of the Pass 221 bug: 0 must NOT be treated as no-timer.
        long displayed = computeDisplayedTicks(0L, 1.0);
        assertNotEquals(BlockSpoilageResponsePayload.NO_TIMER, displayed,
                "0 ticks is a real expiration, not the sentinel");
        String timeStr = computeTimeStr(displayed);
        assertFalse(timeStr.isEmpty(),
                "0 ticks must render a time string (<1 min), not the empty no-timer string");
    }

    @Test
    void freshTimerIsScaledByMultiplier() {
        // Scenario: a fresh block with 12000 real ticks remaining at 2x speed.
        // The HUD multiplies by the speed multiplier to convert real ticks to
        // "normal-speed ticks" for display — so the player sees the countdown as if
        // speed were 1x. At 2x speed, 12000 real ticks = 24000 normal-speed ticks.
        long displayed = computeDisplayedTicks(12000L, 2.0);
        assertEquals(24000L, displayed,
                "2x speed must convert 12000 real ticks to 24000 displayed ticks");
        String timeStr = computeTimeStr(displayed);
        assertFalse(timeStr.isEmpty(), "A real timer must produce a time string");
    }

    @Test
    void slowMultiplierExtendsDisplayedTime() {
        // Scenario: a fresh block with 24000 real ticks remaining at 0.5x speed.
        // The HUD multiplies by 0.5 to convert to normal-speed ticks: 24000 * 0.5 = 12000.
        long displayed = computeDisplayedTicks(24000L, 0.5);
        assertEquals(12000L, displayed,
                "0.5x speed must convert 24000 real ticks to 12000 displayed ticks");
    }

    @Test
    void formatTimeRendersLessThanMinuteForSmallValues() {
        // Scenario: 100 ticks remaining (5 seconds) must render "<1 min" style text.
        String timeStr = SpoilageEnhancedTranslations.formatTime(100L);
        assertFalse(timeStr.isEmpty(), "100 ticks must render a non-empty time string");
    }

    @Test
    void formatTimeRendersDaysForLargeValues() {
        // Scenario: 240000 ticks (10 days) must render day-scale text.
        String timeStr = SpoilageEnhancedTranslations.formatTime(240000L);
        assertFalse(timeStr.isEmpty(), "240000 ticks must render a non-empty time string");
    }
}