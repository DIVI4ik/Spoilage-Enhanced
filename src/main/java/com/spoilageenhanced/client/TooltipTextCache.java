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
     * Bit layout of the cache key (Pass 487 — the inline packing was not injective):
     *
     * <pre>
     *   bits 63..32  identity hash of the stack
     *   bits 31..8   displayedDiff, masked to 22 bits (0..4,194,303 ticks ≈ 48 days)
     *   bits  7..1   stack count, 7 bits (0..127)
     *   bit   0      shift held
     * </pre>
     *
     * <p>The low 30 bits are injective: the diff occupies 22 bits starting at bit 8, the
     * count 7 bits starting at bit 1, and the shift flag bit 0, with no overlap. The
     * caller passes the raw values rather than pre-shifted pieces, so a change to the
     * layout is one edit in one place.</p>
     *
     * @param identityHash  {@link System#identityHashCode} of the stack
     * @param displayedDiff remaining ticks until the next stage, as rendered
     * @param stackCount    {@code ItemStack.getCount()}
     * @param shiftHeld     whether Shift is held for the details view
     */
    public static long key(long identityHash, long displayedDiff, int stackCount, boolean shiftHeld) {
        return (identityHash << 32)
                | ((displayedDiff & 0x3FFFFFL) << 8)
                | ((stackCount & 0x7FL) << 1)
                | (shiftHeld ? 1L : 0L);
    }

    // ConcurrentHashMap so the render thread and the language-load thread do not race.
    private static final java.util.Map<Long, CachedTooltipLines> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static CachedTooltipLines get(long key) {
        return CACHE.get(key);
    }

    public static void put(long key, CachedTooltipLines value) {
        if (CACHE.size() < MAX_ENTRIES) {
            CACHE.put(key, value);
        }
    }

    public static void clear() {
        CACHE.clear();
    }
}