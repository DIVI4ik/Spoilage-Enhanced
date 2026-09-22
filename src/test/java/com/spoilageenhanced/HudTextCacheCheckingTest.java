package com.spoilageenhanced;

import com.spoilageenhanced.client.HudTextCache;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1405 (L5 — render path): cache for HUD "checking" text.
 *
 * <p>BlockSpoilageHudMixin renders a "checking freshness" placeholder while waiting
 * for the server to answer. This text is allocated via Component.translatable and
 * its width measured via font.width() EVERY FRAME while the crosshair rests on an
 * unanswered block. The text never changes — it's always the same translation key.
 * Caching it eliminates the per-frame allocation and font measurement.</p>
 */
public class HudTextCacheCheckingTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void checkingTextCacheKeyIsStable() {
        // The checking text uses a special cache key that doesn't depend on state or ticks
        long key1 = HudTextCache.checkingKey();
        long key2 = HudTextCache.checkingKey();
        assertEquals(key1, key2, "Checking key must be stable");
    }

    @Test
    void checkingTextCacheWorks() {
        long key = HudTextCache.checkingKey();
        assertNull(HudTextCache.get(key), "Cache miss initially");

        // Put a dummy cached text
        var cached = new HudTextCache.CachedHudText(
                net.minecraft.network.chat.Component.literal("checking"),
                100, 0xFFAAAAAA);
        HudTextCache.put(key, cached);

        var retrieved = HudTextCache.get(key);
        assertNotNull(retrieved, "Cache hit after put");
        assertEquals("checking", retrieved.text().getString());
        assertEquals(100, retrieved.width());
        assertEquals(0xFFAAAAAA, retrieved.color());
    }
}
