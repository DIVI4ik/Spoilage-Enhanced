package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 268 regression test: SpoilageConfig.registerDynamicStorageItem.
 *
 * <p>registerDynamicStorageItem registers a storage item (e.g., a shulker box) as
 * spoilable, inheriting durations from a source food item. It validates that the
 * source is spoilable and the storage is a valid food candidate. This test pins
 * the contract.</p>
 */
public class RegisterDynamicStorageItemTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registerWithSpoilableSourceAddsStorageItem() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // EGG is always-spoilable. Use a vanilla item that's not already tracked
        // but passes isValidFoodCandidate (not a tool, armor, seed, etc.).
        // minecraft:bowl is a vanilla item that passes the candidate check.
        config.registerDynamicStorageItem("minecraft:bowl", "minecraft:egg", false);

        assertTrue(config.getAdditionalSet().contains("minecraft:bowl"),
                "The storage item must be added to the additional tracked set");
    }

    @Test
    void registerWithNonSpoilableSourceIsNoOp() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        String storageId = "testmod:storage_nonspoil_" + System.nanoTime();
        // STONE is not spoilable.
        config.registerDynamicStorageItem(storageId, "minecraft:stone", false);

        assertFalse(config.getAdditionalSet().contains(storageId),
                "A storage item with a non-spoilable source must not be added");
    }

    @Test
    void registerWithInvalidStorageItemIsNoOp() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Use a valid source but an invalid storage item id.
        config.registerDynamicStorageItem("nonexistent:invalid_item_xyz", "minecraft:egg", false);

        assertFalse(config.getAdditionalSet().contains("nonexistent:invalid_item_xyz"),
                "An invalid storage item id must not be added");
    }
}
