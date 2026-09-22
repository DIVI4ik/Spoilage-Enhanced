package com.spoilageenhanced.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

/**
 * Pass 187: Tooltip text cache shared between ItemClientMixin (tooltip render path)
 * and potentially other tooltip renderers.
 *
 * <p>The cache holds a per-(stack identity, displayedDiff, shiftHeld, stackCount) tuple
 * of pre-built tooltip lines. The text only changes when displayedDiff changes (every tick)
 * or shift state changes. The cache is tiny (max 64 entries) and cleared on language change
 * alongside the HUD cache.</p>
 *
 * <p>Pass 1412 (L5 — render path): connection fingerprinting added. A recycled identity
 * hash on the same quantized time bucket with matching count/shift would otherwise serve
 * the previous world's cached tooltip lines for a stack in the new world. The fingerprint
 * is checked on every {@link #get} and {@link #put} call so a server switch clears the
 * cache immediately without a disconnect event hook.</p>
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

    /**
     * Pass 1412 (L5 — render path): connection fingerprint. A new connection always has a
     * different identity hash (the old object is GC-eligible), so a connection switch forces
     * the cache to clear even when the quantized time bucket, count and shift happen to match.
     * Checked inside {@link #get} and {@link #put} so no disconnect event hook is needed.
     */
    private static int cachedConnectionHash;

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

    public static CachedTooltipLines get(long key) {
        checkConnection();
        return CACHE.get(key);
    }

    /** Current number of cached entries. Test hook for the cap invariant. */
    public static int size() {
        return CACHE.size();
    }

    public static void put(long key, CachedTooltipLines value) {
        checkConnection();
        // A single put() replaces the old containsKey()+put() pair (two lookups): it returns
        // the previous value, null when the key was absent. Only on a genuine new key does
        // the map grow, and only then do we check the cap — so a re-put of an existing key
        // costs one lookup and never touches the eviction branch.
        if (CACHE.put(key, value) == null && CACHE.size() > MAX_ENTRIES) {
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
    }

    public static void clear() {
        CACHE.clear();
        cachedConnectionHash = 0;
    }
}