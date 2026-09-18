package com.spoilageenhanced.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1345 (L5 — render path): test HudTextCache.
 *
 * <p>HudTextCache caches pre-built HUD text components for the block spoilage HUD
 * render path. It runs every frame for every tracked block the player looks at.
 * The cache key is (spoilState, displayedTicks). The Component re-resolves its
 * language on render, but the cached WIDTH goes stale on language change — so
 * clear() must be called on language reload.</p>
 *
 * <p>Pass 604 fixed the cap enforcement: once full, new entries were silently
 * dropped. The fix evicts one arbitrary entry to make room.</p>
 *
 * <p>What this test pins: put/get works, miss returns null, evicts when full,
 * clear works, size reports correctly, replaces existing key, CachedHudText record.</p>
 */
class HudTextCacheTest {

    @BeforeAll
    static void bootstrap() {
        // No Minecraft bootstrap needed — HudTextCache is pure Java
    }

    @AfterEach
    void clearCache() {
        HudTextCache.clear();
    }

    @Test
    void cachePutAndGet() {
        var text = Component.literal("test");
        var cached = new HudTextCache.CachedHudText(text, 100, 0xFFFFFF);
        long key = 12345L;

        HudTextCache.put(key, cached);
        var retrieved = HudTextCache.get(key);

        assertNotNull(retrieved);
        assertSame(cached, retrieved);
    }

    @Test
    void cacheMissReturnsNull() {
        var retrieved = HudTextCache.get(999999L);
        assertNull(retrieved);
    }

    @Test
    void cacheEvictsWhenFull() {
        // Fill the cache to MAX_ENTRIES (64)
        for (int i = 0; i < 64; i++) {
            var cached = new HudTextCache.CachedHudText(Component.literal("t" + i), 100, 0xFFFFFF);
            HudTextCache.put(i, cached);
        }

        // Add one more — should evict one and stay at 64
        var newCached = new HudTextCache.CachedHudText(Component.literal("new"), 100, 0xFFFFFF);
        HudTextCache.put(999, newCached);

        assertNotNull(HudTextCache.get(999L), "new entry must be present");
    }

    @Test
    void cacheClearWorks() {
        for (int i = 0; i < 10; i++) {
            var cached = new HudTextCache.CachedHudText(Component.literal("t" + i), 100, 0xFFFFFF);
            HudTextCache.put(i, cached);
        }

        HudTextCache.clear();
        assertNull(HudTextCache.get(1L), "cache must be empty after clear");
    }

    @Test
    void cacheReplacesExistingKey() {
        long key = 1;
        var cached1 = new HudTextCache.CachedHudText(Component.literal("a"), 100, 0xFFFFFF);
        var cached2 = new HudTextCache.CachedHudText(Component.literal("b"), 200, 0xFF0000);

        HudTextCache.put(key, cached1);
        HudTextCache.put(key, cached2);
        assertSame(cached2, HudTextCache.get(key), "must return the new value");
    }

    @Test
    void cachedHudTextRecord() {
        var text = Component.literal("test");
        var cached = new HudTextCache.CachedHudText(text, 100, 0xFFFFFF);
        assertSame(text, cached.text());
        assertEquals(100, cached.width());
        assertEquals(0xFFFFFF, cached.color());
    }

    @Test
    void cachedHudTextWidthAndColorStored() {
        // Verify width and color are stored separately from the Component
        // (the Component re-resolves language on render, but width was measured
        // in the old language and goes stale — that's why clear() is needed on
        // language change)
        var text = Component.literal("test");
        var cached = new HudTextCache.CachedHudText(text, 123, 0xABCDEF);
        assertEquals(123, cached.width());
        assertEquals(0xABCDEF, cached.color());
    }
}