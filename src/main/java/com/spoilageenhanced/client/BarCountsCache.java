package com.spoilageenhanced.client;

import com.spoilageenhanced.component.SpoilageData;

/**
 * Pass 1403 (L5 — render path): cache for GuiGraphicsExtractorMixin bar counts.
 *
 * <p>GuiGraphicsExtractorMixin.spoilage_enhanced_drawFreshnessBar runs for EVERY
 * rendered item in EVERY visible inventory slot, EVERY FRAME. The old code iterated
 * through freshExpirations() and staleExpirations() lists on every call to compute
 * freshCount/staleCount/rottenCount. These counts only change when the server sends
 * new spoilage data (on ticks), not every frame. A cache keyed by the stack's
 * component identity + currentTime bucket would eliminate the per-frame iteration
 * and the staleDuration CHM lookup for the common case.</p>
 *
 * <p>The cache key packs: identityHash (32 bits) + currentTime/24000 (day bucket, 16 bits)
 * + data version (16 bits from System.identityHashCode of the SpoilageData instance).
 * This gives a hit rate near 100% within a day bucket for a given stack.</p>
 */
public final class BarCountsCache {

    public record CachedCounts(int freshCount, int staleCount, int rottenCount) {}

    private static final int MAX_ENTRIES = 256;

    private BarCountsCache() {}

    // ConcurrentHashMap so the render thread and the language-load thread do not race.
    private static final java.util.Map<Long, CachedCounts> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Packs the cache key:
     * - bits 63..32: identity hash of the stack
     * - bits 31..16: day bucket (currentTime / 24000)
     * - bits 15..0:  data version (identity hash of SpoilageData instance)
     */
    public static long key(long identityHash, long currentTime, SpoilageData data) {
        long dayBucket = currentTime / 24000L;
        long dataVersion = data != null ? System.identityHashCode(data) : 0L;
        return (identityHash << 32)
                | ((dayBucket & 0xFFFFL) << 16)
                | (dataVersion & 0xFFFFL);
    }

    public static CachedCounts get(long key) {
        return CACHE.get(key);
    }

    public static void put(long key, CachedCounts value) {
        if (CACHE.size() >= MAX_ENTRIES && !CACHE.containsKey(key)) {
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
