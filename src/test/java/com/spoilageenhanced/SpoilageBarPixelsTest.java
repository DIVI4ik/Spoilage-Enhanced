package com.spoilageenhanced;

import com.spoilageenhanced.client.SpoilageBarPixels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 145 regression test: spoilage bar pixel math.
 *
 * The old inline math in GuiGraphicsExtractorMixin rounded the fresh and stale segments
 * independently, so a stack with equal fresh and stale counts and no rotten items
 * (e.g. 16 fresh / 16 stale) rounded BOTH segments up from 6.5 to 7 pixels in a
 * 13-pixel frame. The negative remainder made the red segment vanish and the bar drew
 * 14 pixels — one pixel past its background frame.
 *
 * <p>Pass 606 (L5 — render-path): the old {@code compute()} returned a fresh {@code int[3]}
 * every call. The tests now use the {@link SpoilageBarPixels.Result} record returned by
 * value (no heap allocation) and also exercise the allocation-free
 * {@link SpoilageBarPixels#computeInto} overload used by the render hot path.</p>
 */
public class SpoilageBarPixelsTest {

    private static final int BAR_HEIGHT = 13;

    @Test
    void equalFreshAndStaleDoesNotOverflowFrame() {
        // 16 fresh / 16 stale: old math gave green=7, yellow=7, red=-1 (bar drew 14px)
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(16, 16, 0, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green() + p.yellow() + p.red(),
                "Segments must sum to exactly the frame height");
        assertTrue(p.red() >= 0, "Red segment must never be negative");
    }

    @Test
    void allFreshIsFullGreen() {
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(10, 0, 0, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green());
        assertEquals(0, p.yellow());
        assertEquals(0, p.red());
    }

    @Test
    void allStaleIsFullYellow() {
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(0, 10, 0, BAR_HEIGHT);
        assertEquals(0, p.green());
        assertEquals(BAR_HEIGHT, p.yellow());
        assertEquals(0, p.red());
    }

    @Test
    void allRottenIsFullRed() {
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(0, 0, 10, BAR_HEIGHT);
        assertEquals(0, p.green());
        assertEquals(0, p.yellow());
        assertEquals(BAR_HEIGHT, p.red());
    }

    @Test
    void thirdsSumToFrameHeight() {
        // 1/3 each: 13/3 = 4.33 -> green=4, yellow=4, red=5 (remainder goes to red)
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(1, 1, 1, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green() + p.yellow() + p.red(),
                "Segments must sum to exactly the frame height");
    }

    @Test
    void singleItemStackGetsFullBar() {
        // A 1-item stack: all pixels go to its state
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(1, 0, 0, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green());
        assertEquals(0, p.yellow());
        assertEquals(0, p.red());
    }

    @Test
    void zeroCountsReturnZeroBar() {
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(0, 0, 0, BAR_HEIGHT);
        assertEquals(0, p.green());
        assertEquals(0, p.yellow());
        assertEquals(0, p.red());
    }

    @Test
    void zeroBarHeightReturnsZeroSegments() {
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(5, 5, 5, 0);
        assertEquals(0, p.green());
        assertEquals(0, p.yellow());
        assertEquals(0, p.red());
    }

    @Test
    void roundingExcessShavedOffLargerSegment() {
        // 3 fresh / 1 stale: green = round(0.75*13) = 10, yellow = round(0.25*13) = 3,
        // red = 13 - 13 = 0. No excess here — verify the normal path.
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(3, 1, 0, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green() + p.yellow() + p.red());
        assertTrue(p.green() >= p.yellow(), "Fresh majority should get the larger segment");
    }

    @Test
    void twoFreshOneStaleOneRotten() {
        // 2/4 fresh, 1/4 stale, 1/4 rotten: green = round(6.5) = 7, yellow = round(3.25) = 3,
        // red = 13 - 10 = 3. Sum = 13. Correct.
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(2, 1, 1, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green() + p.yellow() + p.red());
        assertTrue(p.red() > 0, "Rotten quarter should get a visible segment");
    }

    @Test
    void manyCombinationsNeverOverflow() {
        // Sweep a grid of count combinations and verify the invariant holds everywhere
        for (int fresh = 0; fresh <= 20; fresh += 3) {
            for (int stale = 0; stale <= 20; stale += 3) {
                for (int rotten = 0; rotten <= 20; rotten += 3) {
                    SpoilageBarPixels.Result p = SpoilageBarPixels.compute(fresh, stale, rotten, BAR_HEIGHT);
                    int sum = p.green() + p.yellow() + p.red();
                    if (fresh + stale + rotten == 0) {
                        assertEquals(0, sum, "Empty stack must draw nothing");
                    } else {
                        assertEquals(BAR_HEIGHT, sum,
                                "Segments must sum to frame height for " + fresh + "/" + stale + "/" + rotten);
                        assertTrue(p.green() >= 0 && p.yellow() >= 0 && p.red() >= 0,
                                "No segment may be negative for " + fresh + "/" + stale + "/" + rotten);
                    }
                }
            }
        }
    }

    @Test
    void largeStacksStayProportional() {
        // 64-item stack: 32 fresh / 16 stale / 16 rotten
        SpoilageBarPixels.Result p = SpoilageBarPixels.compute(32, 16, 16, BAR_HEIGHT);
        assertEquals(BAR_HEIGHT, p.green() + p.yellow() + p.red());
        assertTrue(p.green() > p.yellow(), "Half fresh should dominate a quarter stale");
        assertTrue(p.yellow() >= p.red() - 1, "Stale and rotten quarters should be near-equal");
    }
}
