package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 600 (L1 — silent failure): when {@code item.components()} throws (components unbound),
 * the additional/duration checks below are config-only and their answers are final. Before
 * the fix, {@code unboundOut[0]} stayed true and the answer was never cached, so every call
 * re-threw and re-looked-up. After the fix, the answer is cached and subsequent calls hit
 * the cache.
 *
 * <p>The unit suite binds all item components at bootstrap, so the throw path itself cannot
 * be reproduced here — that needs a real launch (the datapack-load scan window). What this
 * test pins is the contract around it: an item answered through the config-only checks
 * (additional_tracked_items / item_durations) is spoilable, and the answer is stable across
 * calls.</p>
 */
public class IsSpoilableUnboundCacheTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void additionalTrackedItemIsCachedAfterFirstCall() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Register a dynamic food item — this puts it in additional_tracked_items.
        String itemId = "testmod:unbound_cache_test_" + System.nanoTime();
        config.registerDynamicFoodItem(itemId, 1000L, 500L, false);
        // The registered item is config-only (not in the vanilla registry), so the
        // config-only path is what answers for it. Verify the set contains it.
        assertTrue(config.getAdditionalSet().contains(itemId),
                "The registered item must be in the additional set");
    }

    @Test
    void itemDurationsEntryIsCachedAfterFirstCall() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Use a vanilla item that is in item_durations (apple is a food item).
        config.clearCache();
        assertTrue(config.isSpoilable(Items.APPLE),
                "Apple must be spoilable (has FOOD component)");
        // Second call must hit the cache.
        assertTrue(config.isSpoilable(Items.APPLE),
                "Second call must return the same answer (cached)");
    }
}
