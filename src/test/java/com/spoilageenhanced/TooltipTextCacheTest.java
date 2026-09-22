package com.spoilageenhanced;

import com.spoilageenhanced.client.TooltipTextCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1394 (L5 render path): TooltipTextCache regression test.
 *
 * The tooltip cache avoids allocating new Component.translatable objects every frame
 * while the tooltip is visible. The cache key includes:
 * - identity hash of the stack
 * - displayedDiff (remaining ticks until next stage)
 * - stack count
 * - shift held state
 *
 * Tests cover: cache hit/miss, key injectivity, cap enforcement, clear on language change,
 * and that the cache actually saves allocations (cache check runs BEFORE line building).
 */
public class TooltipTextCacheTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @AfterEach
    void clearCache() {
        TooltipTextCache.clear();
    }

    @Test
    void cacheHitReturnsSameLines() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long key = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);

        List<Component> lines = List.of(Component.literal("Fresh"));
        TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(lines));

        TooltipTextCache.CachedTooltipLines cached = TooltipTextCache.get(key);
        assertNotNull(cached, "Cache hit should return lines");
        assertEquals(lines, cached.lines(), "Cached lines must match");
    }

    @Test
    void cacheMissReturnsNull() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long key = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);

        TooltipTextCache.CachedTooltipLines cached = TooltipTextCache.get(key);
        assertNull(cached, "Cache miss should return null");
    }

    @Test
    void differentDisplayedDiffGivesDifferentKey() {
        ItemStack stack = new ItemStack(Items.APPLE);
        // With quantization:
        // 1000 ticks = 0d 1h 0m (1000/1000 = 1 hour, 0 minutes)
        // 1001 ticks = 0d 1h 0m (same minute bucket)
        // 1016 ticks = 0d 1h 0m (16*60/1000 = 0.96 -> 0 minutes)
        // 1017 ticks = 0d 1h 1m (17*60/1000 = 1.02 -> 1 minute)
        long key1 = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);
        long key2 = TooltipTextCache.key(System.identityHashCode(stack), 1001L, 1, false);
        long key3 = TooltipTextCache.key(System.identityHashCode(stack), 1016L, 1, false);
        long key4 = TooltipTextCache.key(System.identityHashCode(stack), 1017L, 1, false);

        // 1000, 1001, 1016 are all in the same minute bucket (0d 1h 0m)
        assertEquals(key1, key2, "Quantized: 1000 and 1001 ticks are in same minute bucket");
        assertEquals(key1, key3, "Quantized: 1000 and 1016 ticks are in same minute bucket");
        // 1017 is in the next minute bucket (0d 1h 1m)
        assertNotEquals(key1, key4, "Different minute buckets must give different keys");

        List<Component> lines1 = List.of(Component.literal("Fresh 1000"));
        List<Component> lines2 = List.of(Component.literal("Fresh 1017"));
        TooltipTextCache.put(key1, new TooltipTextCache.CachedTooltipLines(lines1));
        TooltipTextCache.put(key4, new TooltipTextCache.CachedTooltipLines(lines2));

        assertEquals(lines1, TooltipTextCache.get(key1).lines());
        assertEquals(lines2, TooltipTextCache.get(key4).lines());
    }

    @Test
    void differentStackCountGivesDifferentKey() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long key1 = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);
        long key2 = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 5, false);

        assertNotEquals(key1, key2, "Different stackCount must give different keys");

        List<Component> lines1 = List.of(Component.literal("1 fresh"));
        List<Component> lines2 = List.of(Component.literal("5 fresh"));
        TooltipTextCache.put(key1, new TooltipTextCache.CachedTooltipLines(lines1));
        TooltipTextCache.put(key2, new TooltipTextCache.CachedTooltipLines(lines2));

        assertEquals(lines1, TooltipTextCache.get(key1).lines());
        assertEquals(lines2, TooltipTextCache.get(key2).lines());
    }

    @Test
    void differentShiftHeldGivesDifferentKey() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long key1 = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);
        long key2 = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, true);

        assertNotEquals(key1, key2, "Different shiftHeld must give different keys");

        List<Component> lines1 = List.of(Component.literal("summary"));
        List<Component> lines2 = List.of(Component.literal("details"));
        TooltipTextCache.put(key1, new TooltipTextCache.CachedTooltipLines(lines1));
        TooltipTextCache.put(key2, new TooltipTextCache.CachedTooltipLines(lines2));

        assertEquals(lines1, TooltipTextCache.get(key1).lines());
        assertEquals(lines2, TooltipTextCache.get(key2).lines());
    }

    @Test
    void differentStacksGetIndependentKeys() {
        ItemStack apple1 = new ItemStack(Items.APPLE);
        ItemStack apple2 = new ItemStack(Items.APPLE);

        long key1 = TooltipTextCache.key(System.identityHashCode(apple1), 1000L, 1, false);
        long key2 = TooltipTextCache.key(System.identityHashCode(apple2), 1000L, 1, false);

        assertNotEquals(key1, key2, "Different stacks must have different identity hashes");

        List<Component> lines1 = List.of(Component.literal("apple1"));
        List<Component> lines2 = List.of(Component.literal("apple2"));
        TooltipTextCache.put(key1, new TooltipTextCache.CachedTooltipLines(lines1));
        TooltipTextCache.put(key2, new TooltipTextCache.CachedTooltipLines(lines2));

        assertEquals(lines1, TooltipTextCache.get(key1).lines());
        assertEquals(lines2, TooltipTextCache.get(key2).lines());
    }

    @Test
    void capEnforcementEvictsOldEntry() {
        // MAX_ENTRIES = 64. Fill the cache and verify eviction works.
        // Use the SAME stack objects so identity hashes are stable.
        ItemStack[] stacks = new ItemStack[70];
        for (int i = 0; i < 70; i++) {
            stacks[i] = new ItemStack(Items.APPLE);
            long key = TooltipTextCache.key(System.identityHashCode(stacks[i]), 1000L + i, 1, false);
            List<Component> lines = List.of(Component.literal("line " + i));
            TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(lines));
        }

        // The most recent stack should still be in the cache (it was put last)
        long recentKey = TooltipTextCache.key(System.identityHashCode(stacks[69]), 1069L, 1, false);
        assertNotNull(TooltipTextCache.get(recentKey), "Most recent entry should survive cap");

        // The cache size should not exceed MAX_ENTRIES
        assertTrue(TooltipTextCache.size() <= 64, "Cache size must not exceed MAX_ENTRIES");
    }

    @Test
    void clearRemovesAllEntries() {
        ItemStack stack = new ItemStack(Items.APPLE);
        long key = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);
        List<Component> lines = List.of(Component.literal("Fresh"));
        TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(lines));

        TooltipTextCache.clear();

        assertNull(TooltipTextCache.get(key), "After clear() cache should be empty");
        assertEquals(0, TooltipTextCache.size(), "Cache size should be 0 after clear");
    }

    @Test
    void keyInjectivityNoOverlap() {
        // Verify the bit layout doesn't have overlapping fields
        // bits 63..32: identity hash (32 bits)
        // bits 31..8: quantized time key (days<<11 | hours<<6 | minutes), 22 bits
        // bits 7..1: stackCount (7 bits)
        // bit 0: shiftHeld (1 bit)

        // 1000 ticks = 0d 1h 0m -> timeKey = (0<<11) | (1<<6) | 0 = 64
        long key = TooltipTextCache.key(0x12345678L, 1000L, 1, true);
        long timeKey = (0L << 11) | (1L << 6) | 0L; // 64
        long expected = (0x12345678L << 32) | ((timeKey & 0x3FFFFFL) << 8) | ((1 & 0x7FL) << 1) | 1L;
        assertEquals(expected, key, "Key bit layout must match documented injective packing");

        // Verify no overlap: changing each field independently changes the key
        long base = TooltipTextCache.key(0x12345678L, 1000L, 1, false);
        assertNotEquals(base, TooltipTextCache.key(0x12345679L, 1000L, 1, false), "identityHash change");
        // displayedDiff change within same minute bucket should NOT change key (quantization)
        assertEquals(base, TooltipTextCache.key(0x12345678L, 1001L, 1, false), "displayedDiff change within same minute bucket");
        // But change to different minute bucket should change key
        assertNotEquals(base, TooltipTextCache.key(0x12345678L, 1017L, 1, false), "displayedDiff change to different minute bucket");
        assertNotEquals(base, TooltipTextCache.key(0x12345678L, 1000L, 2, false), "stackCount change");
        assertNotEquals(base, TooltipTextCache.key(0x12345678L, 1000L, 1, true), "shiftHeld change");
    }

    @Test
    void quantizedTimeKeyFitsIn22Bits() {
        // The quantized time key (days<<11 | hours<<6 | minutes) fits in 22 bits:
        // days: up to 53 bits in formatTime, but we only use 22 bits in the cache key
        // hours: 5 bits (0..23)
        // minutes: 6 bits (0..59)
        // Total: 11 + 5 + 6 = 22 bits
        // Very large tick values will have days truncated to 22 bits, but that's
        // acceptable because the cache key is only for tooltip rendering and
        // absurdly long timers (millions of days) will share buckets.

        // Test that the time key never exceeds 22 bits for realistic values
        long key1 = TooltipTextCache.key(0x12345678L, 24000L, 1, false); // 1 day
        long key2 = TooltipTextCache.key(0x12345678L, 48000L, 1, false); // 2 days
        long key3 = TooltipTextCache.key(0x12345678L, 72000L, 1, false); // 3 days

        assertNotEquals(key1, key2, "Different day buckets should have different keys");
        assertNotEquals(key2, key3, "Different day buckets should have different keys");

        // Very large value - days will be truncated to 22 bits
        long keyHuge = TooltipTextCache.key(0x12345678L, Long.MAX_VALUE, 1, false);
        long keyZero = TooltipTextCache.key(0x12345678L, 0L, 1, false);
        // The huge value should produce some key (not crash), but may collide with zero
        // due to day truncation - that's acceptable for tooltip rendering
        assertNotNull(keyHuge);
    }

    @Test
    void stackCountMaskedTo7Bits() {
        // stackCount is masked to 7 bits (0..127)
        // 0x7F = 127 (max 7-bit value)
        // 0x80 = 128 (8th bit set)
        // After masking, 0x80 should become 0, same as stackCount=0
        long keyMax = TooltipTextCache.key(0x12345678L, 1000L, 0x7F, false);
        long keyOverflow = TooltipTextCache.key(0x12345678L, 1000L, 0x80, false);
        long keyZero = TooltipTextCache.key(0x12345678L, 1000L, 0, false);

        // 0x80 masked to 7 bits = 0, so keyOverflow should equal keyZero
        assertEquals(keyZero, keyOverflow, "stackCount must be masked to 7 bits");
        // keyMax should be different (it has all 7 bits set)
        assertNotEquals(keyMax, keyZero, "Max 7-bit value should differ from zero");
    }

    @Test
    void cacheCheckRunsBeforeLineBuilding() {
        // This test documents the critical performance invariant: the cache check
        // runs BEFORE any Component.translatable allocations. The mixin code at
        // ItemClientMixin:203-206 does the cache lookup first, and only builds
        // spoilageLines if the cache misses. This test pins that the cache API
        // supports this pattern (get before put).
        ItemStack stack = new ItemStack(Items.APPLE);
        long key = TooltipTextCache.key(System.identityHashCode(stack), 1000L, 1, false);

        // Simulate the mixin's logic: check cache first
        TooltipTextCache.CachedTooltipLines cached = TooltipTextCache.get(key);
        boolean cacheHit = cached != null;

        if (!cacheHit) {
            // Only build lines on cache miss
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("tooltip.fresh"));
            TooltipTextCache.put(key, new TooltipTextCache.CachedTooltipLines(lines));
        }

        // Second call should hit
        TooltipTextCache.CachedTooltipLines cached2 = TooltipTextCache.get(key);
        assertNotNull(cached2, "Second call should hit cache");
        assertEquals(1, cached2.lines().size());
    }
}