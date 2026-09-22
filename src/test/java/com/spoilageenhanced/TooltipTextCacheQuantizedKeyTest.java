package com.spoilageenhanced;

import com.spoilageenhanced.client.TooltipTextCache;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1404 (L5 — render path): quantized cache key for TooltipTextCache.
 *
 * <p>The TooltipTextCache key now uses the same quantization as
 * SpoilageEnhancedTranslations.formatTime — (days, hours, minutes) — instead of
 * raw displayedDiff. The tooltip text only changes when the quantized triple
 * changes, so the hit rate increases from ~1/tick to ~1/minute for the minutes
 * bucket, ~1/hour for the hours bucket, etc.</p>
 */
public class TooltipTextCacheQuantizedKeyTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void quantizedKeyChangesLessOften() {
        long identityHash = 0x12345678L;
        int stackCount = 10;
        boolean shiftHeld = false;

        // Current key (quantized displayedDiff)
        // 1000 ticks = 0d 1h 0m
        // 1001 ticks = 0d 1h 0m (same minute bucket)
        // 1002 ticks = 0d 1h 0m (same minute bucket)
        long key1 = TooltipTextCache.key(identityHash, 1000L, stackCount, shiftHeld);
        long key2 = TooltipTextCache.key(identityHash, 1001L, stackCount, shiftHeld);
        long key3 = TooltipTextCache.key(identityHash, 1002L, stackCount, shiftHeld);

        // All three should be the same (same minute bucket)
        assertEquals(key1, key2, "Same minute bucket should have same quantized key");
        assertEquals(key2, key3, "Same minute bucket should have same quantized key");

        // Different minute buckets should have different keys
        // 1000 ticks = 0m, 1060 ticks = 3m (60*60/1000 = 3.6 -> 3)
        long key4 = TooltipTextCache.key(identityHash, 1060L, stackCount, shiftHeld); // 0d 1h 3m
        assertNotEquals(key1, key4, "Different minute buckets should have different keys");
    }

    @Test
    void quantizedKeyMatchesFormatTimeQuantization() {
        // formatTime quantizes to (days, hours, minutes)
        // days = ticks / 24000
        // hours = (ticks % 24000) / 1000
        // minutes = ((ticks % 24000) % 1000) * 60 / 1000

        // 1000 ticks = 0d 1h 0m
        // 1016 ticks = 0d 1h 0m (16*60/1000 = 0.96 -> 0)
        // 1017 ticks = 0d 1h 1m (17*60/1000 = 1.02 -> 1)
        long key1 = TooltipTextCache.key(0x12345678L, 1000L, 1, false);
        long key2 = TooltipTextCache.key(0x12345678L, 1016L, 1, false);
        long key3 = TooltipTextCache.key(0x12345678L, 1017L, 1, false);

        assertEquals(key1, key2, "1000 and 1016 should be same minute bucket (0)");
        assertNotEquals(key2, key3, "1016 and 1017 should be different minute buckets (0 vs 1)");
    }

    @Test
    void zeroAndNegativeDisplayDiffUseSameBucket() {
        long identityHash = 0x12345678L;
        int stackCount = 1;
        boolean shiftHeld = false;

        // displayedDiff <= 0 all map to the same "less than a minute" bucket
        long key0 = TooltipTextCache.key(identityHash, 0L, stackCount, shiftHeld);
        long keyNeg = TooltipTextCache.key(identityHash, -1L, stackCount, shiftHeld);
        long keyNeg2 = TooltipTextCache.key(identityHash, -100L, stackCount, shiftHeld);

        assertEquals(key0, keyNeg, "Zero and negative displayedDiff should use same bucket");
        assertEquals(keyNeg, keyNeg2, "All negative displayedDiff should use same bucket");
    }

    @Test
    void largeTickValuesQuantizeCorrectly() {
        long identityHash = 0x12345678L;
        int stackCount = 1;
        boolean shiftHeld = false;

        // 24000 ticks = 1 day
        // 48000 ticks = 2 days
        // 72000 ticks = 3 days
        long key1 = TooltipTextCache.key(identityHash, 24000L, stackCount, shiftHeld);
        long key2 = TooltipTextCache.key(identityHash, 48000L, stackCount, shiftHeld);
        long key3 = TooltipTextCache.key(identityHash, 72000L, stackCount, shiftHeld);

        assertNotEquals(key1, key2, "Different day buckets should have different keys");
        assertNotEquals(key2, key3, "Different day buckets should have different keys");
    }
}
