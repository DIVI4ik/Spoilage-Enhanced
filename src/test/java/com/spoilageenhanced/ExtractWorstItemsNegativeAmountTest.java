package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 363 regression test: FoodSpoilageUtil.extractWorstItems negative amount guard.
 *
 * <p>extractWorstItems had a boundary inconsistency with extractBestItems: it used
 * Math.min(sourceRotten, amount) without the Math.max(amount, 0) guard. If amount
 * is negative, Math.min(sourceRotten, negative) returns negative, causing sourceRotten
 * to increase and amount to increase — a latent bug. The fix mirrors extractBestItems
 * (line 156) with Math.max(amount, 0). This test fails against the old code and passes
 * with the fix.</p>
 */
public class ExtractWorstItemsNegativeAmountTest {

    @Test
    void negativeAmountDoesNotCorruptRottenCount() {
        // Source: 5 rotten, 0 stale, 0 fresh
        SpoilageData data = new SpoilageData(List.of(), List.of(), 5, 1.0);

        // Old code: rottenTake = min(5, -1) = -1
        // sourceRotten = 5 - (-1) = 6 (INCREASED!)
        // amount = -1 - (-1) = 0 (loop exits)
        // remaining.rottenCount = 6 (WRONG — should be 5)
        // extracted.rottenCount = -1 (WRONG — should be 0)
        //
        // Fixed code: rottenTake = min(5, max(-1, 0)) = min(5, 0) = 0
        // sourceRotten = 5 - 0 = 5 (correct)
        // amount = -1 - 0 = -1 (loop continues, pads with Long.MAX_VALUE)
        // remaining.rottenCount = 5 (correct)
        // extracted.rottenCount = 0 (correct)

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, -1);
        SpoilageData remaining = split[0];
        SpoilageData extracted = split[1];

        // The fix ensures rotten count is not corrupted
        assertEquals(5, remaining.rottenCount(),
                "Remaining rotten count must not increase when amount is negative");
        assertEquals(0, extracted.rottenCount(),
                "Extracted rotten count must be 0 when amount is negative");
    }

    @Test
    void negativeAmountDoesNotCorruptStaleCount() {
        // Source: 0 rotten, 3 stale, 0 fresh
        SpoilageData data = new SpoilageData(List.of(), List.of(1000L, 2000L, 3000L), 0, 1.0);

        // Old code with negative amount would corrupt stale extraction too
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, -1);
        SpoilageData remaining = split[0];
        SpoilageData extracted = split[1];

        // With the fix, stale count should be preserved (amount < 0 means take nothing)
        assertEquals(3, remaining.staleExpirations().size(),
                "Remaining stale count must not change when amount is negative");
        assertEquals(0, extracted.staleExpirations().size(),
                "Extracted stale count must be 0 when amount is negative");
    }

    @Test
    void negativeAmountDoesNotCorruptFreshCount() {
        // Source: 0 rotten, 0 stale, 4 fresh
        SpoilageData data = new SpoilageData(List.of(1000L, 2000L, 3000L, 4000L), List.of(), 0, 1.0);

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, -1);
        SpoilageData remaining = split[0];
        SpoilageData extracted = split[1];

        assertEquals(4, remaining.freshExpirations().size(),
                "Remaining fresh count must not change when amount is negative");
        assertEquals(0, extracted.freshExpirations().size(),
                "Extracted fresh count must be 0 when amount is negative");
    }

    /**
     * Pass 497 (L7 boundary): a 64-stack with 1 fresh entry extracts 64 items — the
     * remaining 63 are padded with {@code Long.MAX_VALUE} (the NEVER sentinel) into
     * the FRESH list (extractWorstItems pads targetFresh, line 170). The returned
     * fresh list must have exactly 64 entries: the one real entry plus 63 NEVER
     * pads, and the remaining list must be empty.
     */
    @Test
    void sixtyFourStackExtractsAllPadsWithNever() {
        SpoilageData data = new SpoilageData(List.of(5000L), List.of(), 0, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 64);
        assertEquals(64, split[1].freshExpirations().size(),
                "Extracted fresh list must have 64 entries (1 real + 63 NEVER pads)");
        assertEquals(0, split[1].staleExpirations().size(),
                "Extracted stale list must be empty");
        assertEquals(0, split[0].freshExpirations().size(),
                "Remaining fresh list must be empty");
        // First entry is the real one (worst-first: the only entry)
        assertEquals(5000L, split[1].freshExpirations().get(0));
        // Remaining 63 fresh are NEVER sentinels
        for (int i = 1; i < 64; i++) {
            assertEquals(Long.MAX_VALUE, split[1].freshExpirations().get(i),
                    "Pad at index " + i + " must be NEVER");
        }
    }
}