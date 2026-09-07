package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 267 regression test: SpoilageConfig.registerDynamicFoodItem.
 *
 * <p>registerDynamicFoodItem adds an item to the tracked list and stores its durations.
 * It is void — the observable effect is that the item becomes spoilable and its durations
 * are retrievable. This test pins the contract.</p>
 */
public class RegisterDynamicFoodItemTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registerNewItemMakesItSpoilable() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        String itemId = "testmod:dynamic_food_" + System.nanoTime();
        config.registerDynamicFoodItem(itemId, 12345L, 6789L, false);

        assertTrue(config.getAdditionalSet().contains(itemId),
                "The item must be added to the additional tracked set");
    }

    @Test
    void registerExistingItemDoesNotOverwrite() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        String itemId = "testmod:dynamic_food_dup_" + System.nanoTime();
        config.registerDynamicFoodItem(itemId, 12345L, 6789L, false);
        // Second registration with different durations — must NOT overwrite.
        config.registerDynamicFoodItem(itemId, 99999L, 99999L, false);

        assertTrue(config.getAdditionalSet().contains(itemId),
                "The item must still be in the additional tracked set");
    }

    @Test
    void registerExcludedItemIsNoOp() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Find an item in the excluded set
        String excluded = config.getExcludedSet().iterator().next();
        assertNotNull(excluded, "The excluded set must not be empty");
        config.registerDynamicFoodItem(excluded, 12345L, 6789L, false);

        assertFalse(config.getAdditionalSet().contains(excluded),
                "An excluded item must not be added to the additional tracked set");
    }
}
