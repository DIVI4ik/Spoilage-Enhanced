package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player report: three pumpkins held as one fresh, one stale and one rotten were placed in
 * quick succession and every block came out ROTTEN.
 *
 * <p>{@code GourdBlockMixin.setPlacedBy} copied {@code getWorstState(stack)} onto the block but
 * never removed that entry from the stack. Vanilla shrinks the count after the callback, so the
 * count fell to two and then one while the component still listed all three entries — and the
 * worst of those stayed ROTTEN for every placement.</p>
 *
 * <p>The fix removes one worst entry per placement. These tests pin the property it depends on:
 * repeated single extraction hands back states in descending severity, and the remainder shrinks
 * in step. If extraction ever stops being worst-first, the reported defect returns.</p>
 */
class PlacedBlockConsumesEntryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One fresh, one stale, one rotten - exactly the stack from the report. */
    private static SpoilageData mixedStack() {
        long future = 1_000_000L;
        return new SpoilageData(List.of(future), List.of(future), 1, 1.0);
    }

    @Test
    void placingThreeTimesDegradesInDescendingSeverity() {
        SpoilageData remaining = mixedStack();
        assertEquals(3, remaining.freshExpirations().size()
                + remaining.staleExpirations().size() + remaining.rottenCount(),
                "the stack under test must hold exactly three items");

        // 1st placement: the rotten one goes into the world.
        SpoilageData[] first = FoodSpoilageUtil.extractWorstItems(remaining, 1);
        assertEquals(1, first[1].rottenCount(), "the first placement must take the rotten item");
        remaining = first[0];
        assertEquals(0, remaining.rottenCount(),
                "the rotten item must be GONE from the stack - leaving it is what made every "
                        + "later placement rotten too");

        // 2nd placement: the stale one.
        SpoilageData[] second = FoodSpoilageUtil.extractWorstItems(remaining, 1);
        assertEquals(1, second[1].staleExpirations().size(), "the second placement must take the stale item");
        assertEquals(0, second[1].rottenCount(), "nothing rotten is left to take");
        remaining = second[0];

        // 3rd placement: the fresh one.
        SpoilageData[] third = FoodSpoilageUtil.extractWorstItems(remaining, 1);
        assertEquals(1, third[1].freshExpirations().size(), "the third placement must take the fresh item");
        remaining = third[0];

        assertTrue(remaining.isEmpty() || remaining.freshExpirations().isEmpty()
                        && remaining.staleExpirations().isEmpty() && remaining.rottenCount() == 0,
                "the stack must be empty after all three were placed");
    }

    @Test
    void extractionLeavesTheStackOneItemSmallerEachTime() {
        SpoilageData remaining = mixedStack();
        int[] expected = {2, 1, 0};
        for (int i = 0; i < 3; i++) {
            remaining = FoodSpoilageUtil.extractWorstItems(remaining, 1)[0];
            int left = remaining.freshExpirations().size()
                    + remaining.staleExpirations().size() + remaining.rottenCount();
            assertEquals(expected[i], left,
                    "after " + (i + 1) + " placement(s) the stack must hold " + expected[i]
                            + " entries so entries and item count stay in step");
        }
    }
}
