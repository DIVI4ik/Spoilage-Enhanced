package com.spoilageenhanced.client;

import com.spoilageenhanced.component.SpoilageData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

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
 *
 * <p>Pass 1411 (L5 — render path): connection fingerprinting added. A recycled identity
 * hash on the same day bucket with a matching 16-bit data version would otherwise serve
 * the previous world's bar counts for a stack in the new world. The fingerprint is checked
 * on every {@link #get} and {@link #put} call (the hot path) so a server switch clears the
 * cache immediately without a disconnect event hook.</p>
 */
public final class BarCountsCache {

    public record CachedCounts(int freshCount, int staleCount, int rottenCount) {}

    private static final int MAX_ENTRIES = 256;

    private BarCountsCache() {}

    // ConcurrentHashMap so the render thread and the language-load thread do not race.
    private static final java.util.Map<Long, CachedCounts> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Pass 1411 (L5 — render path): connection fingerprint. A new connection always has a
     * different identity hash (the old object is GC-eligible), so a connection switch forces
     * the cache to clear even when the day bucket and data version happen to match.
     * Checked inside {@link #get} and {@link #put} so no disconnect event hook is needed.
     */
    private static int cachedConnectionHash;

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

    private static int currentConnectionHash() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return 0;
        ClientPacketListener conn = client.getConnection();
        return conn == null ? 0 : System.identityHashCode(conn);
    }

    private static void checkConnection() {
        int connHash = currentConnectionHash();
        if (connHash != cachedConnectionHash) {
            CACHE.clear();
            cachedConnectionHash = connHash;
        }
    }

    public static CachedCounts get(long key) {
        checkConnection();
        return CACHE.get(key);
    }

    public static void put(long key, CachedCounts value) {
        checkConnection();
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
        cachedConnectionHash = 0;
    }
}
