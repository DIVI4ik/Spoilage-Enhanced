package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for negative ratio in rescaleItemTimestamps stale branch.
 * The stale branch was missing the `ratio <= 0.0d` guard that the fresh branch has.
 */
public class RescaleNegativeRatioTest {

    @Test
    void negativeRatioClampsStaleToNever() {
        // Stale entry with remaining = 1000 at currentTime=1000
        SpoilageData data = new SpoilageData(
                List.of(),  // fresh
                List.of(2000L),  // stale: expiration at 2000, currentTime=1000 -> remaining=1000
                0, 1.0);

        // Negative ratio should be treated as invalid and clamp to NEVER
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, -1.0, 1.0);

        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Negative ratio must clamp stale expiration to NEVER (Long.MAX_VALUE)");
    }

    @Test
    void zeroRatioClampsStaleToNever() {
        SpoilageData data = new SpoilageData(
                List.of(),
                List.of(2000L),
                0, 1.0);

        // Zero ratio should be treated as invalid and clamp to NEVER
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, 0.0, 1.0);

        assertEquals(Long.MAX_VALUE, result.staleExpirations().get(0),
                "Zero ratio must clamp stale expiration to NEVER");
    }

    @Test
    void negativeRatioClampsFreshToNever() {
        // Fresh entry with remaining = 1000
        SpoilageData data = new SpoilageData(
                List.of(2000L),
                List.of(), 0, 1.0);

        // Negative ratio should be treated as invalid and clamp to NEVER
        SpoilageData result = FoodSpoilageUtil.rescaleItemTimestamps(data, 1000L, -1.0, 1.0);

        assertEquals(Long.MAX_VALUE, result.freshExpirations().get(0),
                "Negative ratio must clamp fresh expiration to NEVER");
    }
}
