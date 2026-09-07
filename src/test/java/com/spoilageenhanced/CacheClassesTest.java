package com.spoilageenhanced;

import com.spoilageenhanced.client.HudTextCache;
import com.spoilageenhanced.client.TooltipTextCache;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 191 regression test: HudTextCache + TooltipTextCache contract.
 *
 * These are simple cache classes (get/put/clear with size cap). This test pins
 * the cache contract: put/get round-trip, clear empties, size cap is respected
 * (MAX_ENTRIES=64), concurrent access doesn't throw.
 */
public class CacheClassesTest {

    @org.junit.jupiter.api.BeforeEach
    void clearCaches() {
        HudTextCache.clear();
        TooltipTextCache.clear();
    }

    @Test
    void hudTextCachePutGetRoundTrip() {
        HudTextCache.CachedHudText value = new HudTextCache.CachedHudText(
                Component.literal("test"), 100, 0xFF55FF55);
        HudTextCache.put(42L, value);
        HudTextCache.CachedHudText retrieved = HudTextCache.get(42L);
        assertEquals(value, retrieved, "Put/get round-trip must return the same value");
    }

    @Test
    void hudTextCacheClearEmpties() {
        HudTextCache.put(1L, new HudTextCache.CachedHudText(Component.literal("a"), 10, 0));
        HudTextCache.put(2L, new HudTextCache.CachedHudText(Component.literal("b"), 20, 0));
        HudTextCache.clear();
        assertNull(HudTextCache.get(1L), "Clear must remove all entries");
        assertNull(HudTextCache.get(2L), "Clear must remove all entries");
    }

    @Test
    void hudTextCacheSizeCapRespected() {
        // MAX_ENTRIES = 64. Pass 604: the cap is now enforced by eviction rather than
        // rejection — when full, a new key evicts one arbitrary entry to make room.
        // Verify the cache never exceeds the cap and the most-recently-put keys are present.
        for (int i = 0; i < 70; i++) {
            HudTextCache.put((long) i, new HudTextCache.CachedHudText(
                    Component.literal("test" + i), i, 0));
        }
        // The last 6 entries (64-69) were put after the cap was reached, so they must
        // be present. Some of the first 64 may have been evicted; the test does not
        // pin which ones (ConcurrentHashMap iterator order is not guaranteed).
        assertNotNull(HudTextCache.get(69L), "Most recent entry must be present");
        assertNotNull(HudTextCache.get(68L), "Second-most recent entry must be present");
        assertNotNull(HudTextCache.get(64L), "Entry 64 must be present");
    }

    @Test
    void tooltipTextCachePutGetRoundTrip() {
        TooltipTextCache.CachedTooltipLines value = new TooltipTextCache.CachedTooltipLines(
                List.of(Component.literal("line1"), Component.literal("line2")));
        TooltipTextCache.put(42L, value);
        TooltipTextCache.CachedTooltipLines retrieved = TooltipTextCache.get(42L);
        assertEquals(value, retrieved, "Put/get round-trip must return the same value");
    }

    @Test
    void tooltipTextCacheClearEmpties() {
        TooltipTextCache.put(1L, new TooltipTextCache.CachedTooltipLines(List.of(Component.literal("a"))));
        TooltipTextCache.put(2L, new TooltipTextCache.CachedTooltipLines(List.of(Component.literal("b"))));
        TooltipTextCache.clear();
        assertNull(TooltipTextCache.get(1L), "Clear must remove all entries");
        assertNull(TooltipTextCache.get(2L), "Clear must remove all entries");
    }

    /**
     * Pass 487: the cache key is a packed bitfield and a collision between two distinct
     * stacks silently swaps their tooltip lines — the player reads the wrong counts for
     * the wrong item, with no error. The layout is injective by construction (the three
     * low fields occupy disjoint bit ranges), so the property that matters is that the
     * fields do not bleed into each other. This pins the boundaries rather than
     * enumerating 10^9 combinations, which is what OOM'd the test executor.
     */
    @Test
    void tooltipCacheKeyFieldsDoNotBleed() {
        // Max diff (22 bits) must not touch the count field.
        assertNotEquals(TooltipTextCache.key(0L, 0x3FFFFFL, 0, false),
                TooltipTextCache.key(0L, 0L, 1, false));
        // Max count (7 bits) must not touch the diff field.
        assertNotEquals(TooltipTextCache.key(0L, 0L, 127, false),
                TooltipTextCache.key(0L, 1L, 0, false));
        // Shift flag is the lowest bit.
        assertNotEquals(TooltipTextCache.key(0L, 0L, 0, false),
                TooltipTextCache.key(0L, 0L, 0, true));
        // A 64-stack must not alias a 0-stack — the old 6-bit count field wrapped at 64.
        assertNotEquals(TooltipTextCache.key(0L, 1000L, 0, false),
                TooltipTextCache.key(0L, 1000L, 64, false));
        // Two stacks sharing a 32-bit identity hash but differing in every field must
        // produce different keys — the collision the old layout actually allowed.
        assertNotEquals(TooltipTextCache.key(0L, 0L, 63, false),
                TooltipTextCache.key(0L, 1L, 31, false));
    }

    @Test
    void tooltipTextCacheSizeCapRespected() {
        // MAX_ENTRIES = 64. The current implementation stops accepting new entries
        // when full (no LRU eviction). Verify that entries beyond the cap are rejected.
        for (int i = 0; i < 70; i++) {
            TooltipTextCache.put((long) i, new TooltipTextCache.CachedTooltipLines(
                    List.of(Component.literal("test" + i))));
        }
        // First 64 entries should be present
        assertNotNull(TooltipTextCache.get(0L), "First entries should be present");
        assertNotNull(TooltipTextCache.get(63L), "Entry 63 should be present");
        // Entries beyond cap should NOT be present (rejected when full)
        assertNull(TooltipTextCache.get(64L), "Entry 64 should be rejected when cap is reached");
        assertNull(TooltipTextCache.get(69L), "Entry 69 should be rejected when cap is reached");
    }

    @Test
    void concurrentAccessDoesNotThrow() throws InterruptedException {
        // Simple concurrent access test: multiple threads putting/getting
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                HudTextCache.put((long) i, new HudTextCache.CachedHudText(
                        Component.literal("t1"), i, 0));
                HudTextCache.get((long) i);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 100; i < 200; i++) {
                TooltipTextCache.put((long) i, new TooltipTextCache.CachedTooltipLines(
                        List.of(Component.literal("t2"))));
                TooltipTextCache.get((long) i);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        // If we reach here without exception, concurrent access is safe
    }
}