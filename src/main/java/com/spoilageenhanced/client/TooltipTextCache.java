package com.spoilageenhanced.client;

import net.minecraft.network.chat.Component;

/**
 * Pass 187: Tooltip text cache shared between ItemClientMixin (tooltip render path)
 * and potentially other tooltip renderers.
 *
 * <p>The cache holds a per-(stack identity, displayedDiff, shiftHeld, stackCount) tuple
 * of pre-built tooltip lines. The text only changes when displayedDiff changes (every tick)
 * or shift state changes. The cache is tiny (max 64 entries) and cleared on language change
 * alongside the HUD cache.</p>
 */
public final class TooltipTextCache {

    public record CachedTooltipLines(java.util.List<Component> lines) {}

    private static final int MAX_ENTRIES = 64;

    private TooltipTextCache() {}

    /**
     * Bit layout of the cache key (Pass 487 — the inline packing was not injective;
     * Pass 1404 — quantized to match formatTime):
     *
     * <pre>
     *   bits 63..32  identity hash of the stack
     *   bits 31..8   quantized time key (days<<11 | hours<<6 | minutes), 22 bits
     *   bits  7..1   stack count, 7 bits (0..127)
     *   bit   0      shift held
     * </pre>
     *
     * <p>The low 30 bits are injective: the quantized time key occupies 22 bits starting
     * at bit 8, the count 7 bits starting at bit 1, and the shift flag bit 0, with no
     * overlap. The caller passes the raw values rather than pre-shifted pieces, so a
     * change to the layout is one edit in one place.</p>
     *
     * <p>Pass 1404 (L5 — render path): the key now uses the same quantization as
     * {@link SpoilageEnhancedTranslations#formatTime} — (days, hours, minutes) — instead
     * of raw displayedDiff. The tooltip text only changes when the quantized triple
     * changes, so the hit rate increases from ~1/tick to ~1/minute for the minutes
     * bucket, ~1/hour for the hours bucket, etc.</p>
     *
     * @param identityHash  {@link System#identityHashCode} of the stack
     * @param displayedDiff remaining ticks until the next stage, as rendered
     * @param stackCount    {@code ItemStack.getCount()}
     * @param shiftHeld     whether Shift is held for the details view
     */
    public static long key(long identityHash, long displayedDiff, int stackCount, boolean shiftHeld) {
        // Quantize displayedDiff to (days, hours, minutes) — same as formatTime
        long timeKey;
        if (displayedDiff <= 0) {
            timeKey = 0L; // "less than a minute" bucket
        } else {
            long days = displayedDiff / 24000L;
            long remainingAfterDays = displayedDiff % 24000L;
            long hours = remainingAfterDays / 1000L;
            long remainingAfterHours = remainingAfterDays % 1000L;
            long minutes = (remainingAfterHours * 60L) / 1000L;
            timeKey = (days << 11) | (hours << 6) | minutes;
        }
        return (identityHash << 32)
                | ((timeKey & 0x3FFFFFL) << 8)
                | ((stackCount & 0x7FL) << 1)
                | (shiftHeld ? 1L : 0L);
    }

    // ConcurrentHashMap so the render thread and the language-load thread do not race.
    private static final java.util.Map<Long, CachedTooltipLines> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static CachedTooltipLines get(long key) {
        return CACHE.get(key);
    }

    /** Current number of cached entries. Test hook for the cap invariant. */
    public static int size() {
        return CACHE.size();
    }

    public static void put(long key, CachedTooltipLines value) {
        if (CACHE.size() >= MAX_ENTRIES && !CACHE.containsKey(key)) {
            // Pass 604 (L3/L4 — cache correctness) fixed this for HudTextCache; TooltipTextCache
            // was left dropping new entries silently once full. A creative inventory with many
            // distinct stacks fills 64 entries in a few glances, and from then on every tooltip
            // for a stack not already cached is rebuilt every frame — the exact allocation the
            // cache exists to avoid, with no log line to say it had stopped working. Evict one
            // arbitrary entry to make room; the next put for the evicted key re-adds it, and
            // the worst case is one extra Component allocation per frame for that stack.
            var first = CACHE.keySet().iterator();
            if (first.hasNext()) {
                CACHE.remove(first.next());
            }
        }
        CACHE.put(key, value);
    }

    public static void clear() {
        CACHE.clear();
    }
}