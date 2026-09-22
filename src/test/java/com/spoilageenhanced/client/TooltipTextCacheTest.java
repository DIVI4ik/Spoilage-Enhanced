package com.spoilageenhanced.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1344 (L5 — render path): test TooltipTextCache.
 *
 * <p>TooltipTextCache caches pre-built tooltip lines for the item tooltip render path.
 * It runs every frame for every hovered stack. The cache key packs identity hash,
 * displayed diff, stack count, and shift state into a single long.</p>
 *
 * <p>Pass 487 fixed a non-injective key packing bug. Pass 604 fixed the cap
 * enforcement: once full, new entries were silently dropped. The fix evicts
 * one arbitrary entry to make room.</p>
 *
 * <p>What this test pins: the key packing is injective, the cache evicts when
 * full, clear() works, and size() reports correctly.</p>
 */
class TooltipTextCacheTest {

    @BeforeAll
    static void bootstrap() {
        // No Minecraft bootstrap needed — TooltipTextCache is pure Java
    }

    @AfterEach
    void clearCache() {
        TooltipTextCache.clear();
    }

    @Test
    void keyPackingIsInjective() {
        // Different inputs must produce different keys
        // With quantization: 500 and 501 ticks are in the same minute bucket (0d 0h 30m)
        // So they produce the SAME key now. But 500 and 517 are different (517 -> 31m)
        long k1 = TooltipTextCache.key(100, 500, 1, false);
        long k2 = TooltipTextCache.key(100, 500, 1, true);  // shift differs
        long k3 = TooltipTextCache.key(100, 500, 2, false); // count differs
        long k4 = TooltipTextCache.key(100, 517, 1, false); // diff differs (different minute bucket)
        long k5 = TooltipTextCache.key(101, 500, 1, false); // identity differs

        assertNotEquals(k1, k2, "shift flag must change key");
        assertNotEquals(k1, k3, "stack count must change key");
        assertNotEquals(k1, k4, "displayed diff (different minute bucket) must change key");
        assertNotEquals(k1, k5, "identity hash must change key");

        // Same minute bucket should produce same key
        long k6 = TooltipTextCache.key(100, 501, 1, false);
        assertEquals(k1, k6, "500 and 501 ticks are in same minute bucket (quantized)");
    }

    @Test
    void keyPackingBitLayout() {
        // Verify the bit layout matches the documented spec (Pass 1404 — quantized):
        // bits 63..32: identity hash
        // bits 31..8:  quantized time key (days<<11 | hours<<6 | minutes), 22 bits
        // bits 7..1:   stackCount (7 bits)
        // bit 0:       shiftHeld
        long identityHash = 0x12345678L;
        // 1000 ticks = 0d 1h 0m -> timeKey = (0<<11) | (1<<6) | 0 = 64
        long displayedDiff = 1000L;
        int stackCount = 0x7F; // max 7 bits
        boolean shiftHeld = true;

        long key = TooltipTextCache.key(identityHash, displayedDiff, stackCount, shiftHeld);

        assertEquals(identityHash, key >>> 32, "identity hash in upper 32 bits");
        // The time key is 64 (0d 1h 0m)
        long timeKey = (key >>> 8) & 0x3FFFFFL;
        assertEquals(64L, timeKey, "quantized time key in bits 31..8");
        assertEquals(stackCount, (key >>> 1) & 0x7FL, "stackCount in bits 7..1");
        assertEquals(1L, key & 1L, "shiftHeld in bit 0");
    }

    @Test
    void keyPackingNoOverlap() {
        // The low 30 bits must be injective: quantized time key (22 bits at 8), count (7 bits at 1), shift (bit 0)
        // No overlap between these fields
        // Use displayedDiff = 1000 ticks = 0d 1h 0m -> timeKey = (0<<11) | (1<<6) | 0 = 64
        long displayedDiff = 1000L; // 0d 1h 0m
        long timeKey = (0L << 11) | (1L << 6) | 0L; // 64
        long k1 = TooltipTextCache.key(0, displayedDiff, 0, false); // time key
        long k2 = TooltipTextCache.key(0, 0, 0x7F, false);          // max count
        long k3 = TooltipTextCache.key(0, 0, 0, true);              // shift

        // Each should only affect its own bits
        assertEquals(timeKey << 8, k1 & (0x3FFFFFL << 8), "time key only in bits 31..8");
        assertEquals(0x7F << 1, k2 & 0xFE, "count only in bits 7..1");
        assertEquals(1L, k3 & 1L, "shift only in bit 0");
    }

    @Test
    void cachePutAndGet() {
        var lines = new TooltipTextCache.CachedTooltipLines(java.util.List.of());
        long key = TooltipTextCache.key(1, 100, 1, false);

        TooltipTextCache.put(key, lines);
        var retrieved = TooltipTextCache.get(key);

        assertNotNull(retrieved);
        assertSame(lines, retrieved);
    }

    @Test
    void cacheMissReturnsNull() {
        var retrieved = TooltipTextCache.get(999999L);
        assertNull(retrieved);
    }

    @Test
    void cacheEvictsWhenFull() {
        // Fill the cache to MAX_ENTRIES (64)
        for (int i = 0; i < 64; i++) {
            long key = TooltipTextCache.key(i, 100, 1, false);
            TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(java.util.List.of()));
        }
        assertEquals(64, TooltipTextCache.size());

        // Add one more — should evict one and stay at 64
        long newKey = TooltipTextCache.key(999, 100, 1, false);
        TooltipTextCache.put(newKey, new TooltipTextCache.CachedTooltipLines(java.util.List.of()));

        assertEquals(64, TooltipTextCache.size(), "cache must not exceed MAX_ENTRIES");
        assertNotNull(TooltipTextCache.get(newKey), "new entry must be present");
    }

    @Test
    void cacheClearWorks() {
        for (int i = 0; i < 10; i++) {
            long key = TooltipTextCache.key(i, 100, 1, false);
            TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(java.util.List.of()));
        }
        assertEquals(10, TooltipTextCache.size());

        TooltipTextCache.clear();
        assertEquals(0, TooltipTextCache.size());
    }

    @Test
    void cacheSizeReportsCorrectly() {
        assertEquals(0, TooltipTextCache.size());
        TooltipTextCache.put(TooltipTextCache.key(1, 100, 1, false),
                new TooltipTextCache.CachedTooltipLines(java.util.List.of()));
        assertEquals(1, TooltipTextCache.size());
        TooltipTextCache.put(TooltipTextCache.key(2, 100, 1, false),
                new TooltipTextCache.CachedTooltipLines(java.util.List.of()));
        assertEquals(2, TooltipTextCache.size());
    }

    @Test
    void cacheReplacesExistingKey() {
        long key = TooltipTextCache.key(1, 100, 1, false);
        var lines1 = new TooltipTextCache.CachedTooltipLines(java.util.List.of());
        var lines2 = new TooltipTextCache.CachedTooltipLines(java.util.List.of());

        TooltipTextCache.put(key, lines1);
        assertEquals(1, TooltipTextCache.size());

        TooltipTextCache.put(key, lines2);
        assertEquals(1, TooltipTextCache.size(), "replacing existing key must not increase size");
        assertSame(lines2, TooltipTextCache.get(key), "must return the new value");
    }

    @Test
    void cachedTooltipLinesRecord() {
        java.util.List<net.minecraft.network.chat.Component> lines = java.util.List.of();
        var cached = new TooltipTextCache.CachedTooltipLines(lines);
        assertSame(lines, cached.lines());
    }
}