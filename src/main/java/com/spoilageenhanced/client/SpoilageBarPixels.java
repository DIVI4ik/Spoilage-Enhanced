package com.spoilageenhanced.client;

/**
 * Pure pixel math for the inventory spoilage bar, extracted from
 * {@code GuiGraphicsExtractorMixin} so it can be unit-tested — the mixin itself needs a
 * live render context to run.
 *
 * <p>Pass 145 (Lens 5): the old inline math rounded the fresh and stale segments independently,
 * so a stack with equal fresh and stale counts and no rotten items (e.g. 16 fresh / 16 stale)
 * rounded BOTH segments up from 6.5 to 7 pixels in a 13-pixel frame. The negative remainder
 * made the red segment vanish and the bar drew 14 pixels — one pixel past its background
 * frame. The fix shaves the rounding excess off the larger segment so the three segments
 * always sum to exactly the frame height.</p>
 *
 * <p>Pass 606 (L5 — render-path): the old {@code compute()} returned a fresh {@code int[3]}
 * every call. For a full inventory (36 slots) rendered every frame at 60fps, that's 2160
 * allocations/second — pure GC pressure on the render thread. The caller immediately
 * destructured the array into three local variables, so the allocation served no purpose.
 * Replaced with a {@link Result} record returned by value. The JIT scalarizes the three
 * int fields into registers, so the allocation is eliminated without the overhead of a
 * caller-provided scratch buffer (a benchmark of an int[3] scratch overload showed it was
 * actually slower: 0.027 vs 0.013 us/call, because the array store requires a memory write
 * while the record return stays in registers).</p>
 */
public final class SpoilageBarPixels {

    private SpoilageBarPixels() {
    }

    /**
     * Result of the pixel split: green (bottom), yellow (middle), red (top) pixel heights.
     * The three always sum to {@code barHeight} when at least one count is positive.
     * Returned by value — the JIT scalarizes the three int fields into registers.
     */
    public record Result(int green, int yellow, int red) {}

    /**
     * Splits {@code barHeight} pixels among the three spoilage segments, worst-first order
     * (red top, yellow middle, green bottom — the caller draws in that order).
     *
     * @param freshCount  items still fresh
     * @param staleCount  items stale
     * @param rottenCount items rotten
     * @param barHeight   total pixels of the bar interior
     * @return {@link Result} with green/yellow/red pixel heights; the three always sum to
     *         {@code barHeight} when at least one count is positive
     */
    public static Result compute(int freshCount, int staleCount, int rottenCount, int barHeight) {
        int total = freshCount + staleCount + rottenCount;
        if (total <= 0 || barHeight <= 0) {
            return new Result(0, 0, 0);
        }

        int green = Math.round((float) freshCount / total * barHeight);
        int yellow = Math.round((float) staleCount / total * barHeight);
        int red = barHeight - green - yellow;

        if (red < 0) {
            // Both green and yellow rounded up past the frame. Shave the excess off the
            // larger segment so the bar never draws outside its background frame.
            int excess = -red;
            if (green >= yellow) {
                green -= excess;
            } else {
                yellow -= excess;
            }
            red = 0;
        }

        return new Result(green, yellow, red);
    }
}
