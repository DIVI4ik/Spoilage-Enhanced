package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 326 regression test: SpoilageConfig.getAdditionalSet.
 *
 * <p>getAdditionalSet returns the set of dynamically registered item IDs (items that
 * were added via registerDynamicFoodItem or registerDynamicStorageItem). It lazily
 * creates a CHM-backed set from the additional_tracked_items list. This test pins
 * the contract: non-null, consistent (cached instance).</p>
 */
public class GetAdditionalSetTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void additionalSetIsNonNull() {
        assertNotNull(SpoilageConfig.getInstance().getAdditionalSet(),
                "getAdditionalSet must return a non-null set");
    }

    @Test
    void additionalSetIsConsistent() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Calling twice must return the same set (cached).
        assertSame(config.getAdditionalSet(), config.getAdditionalSet(),
                "getAdditionalSet must return the same instance on repeat calls");
    }

    @Test
    void additionalSetContainsRegisteredItems() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Register a dynamic food item
        String itemId = "testmod:dynamic_test_" + System.nanoTime();
        config.registerDynamicFoodItem(itemId, 1000L, 500L, false);
        assertTrue(config.getAdditionalSet().contains(itemId),
                "The registered item must be in the additional set");
    }
}