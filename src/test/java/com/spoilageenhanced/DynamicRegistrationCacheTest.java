package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 617 (Lens 3 — cache correctness): dynamic item registration must invalidate the
 * lazy additionalSet / derivedSet caches.
 *
 * <p>Before this fix, registerDynamicStorageItem / registerDynamicFoodItem cleared
 * spoilableCache and BASE_DURATION_CACHE but left the precomputed additionalSet field
 * pointing at its pre-registration snapshot. A newly registered item was therefore
 * invisible to isSpoilable() until the next /spoilage config reload (the only other
 * path that nulls it). This test pins the contract by exercising getAdditionalSet()
 * directly — the field that was left stale.
 */
public class DynamicRegistrationCacheTest {

    // A real item id that is not in the default excluded list and not already tracked.
    // (Golden apple and chorus fruit are excluded by default; a carrot is not.)
    private static final String TEST_ITEM_ID = "minecraft:carrot";

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void cleanup() {
        SpoilageConfig.reload();
    }

    @Test
    void additionalSetReflectsDynamicFoodRegistration() {
        SpoilageConfig config = SpoilageConfig.getInstance();

        // Prime the lazy additionalSet cache so the field is populated before registration.
        config.getAdditionalSet();

        config.registerDynamicFoodItem(TEST_ITEM_ID, 24000L, 24000L);

        assertTrue(config.getAdditionalSet().contains(TEST_ITEM_ID),
                "additionalSet must be invalidated by registerDynamicFoodItem");
    }

    @Test
    void additionalSetReflectsDynamicStorageRegistration() {
        SpoilageConfig config = SpoilageConfig.getInstance();

        // Prime the lazy additionalSet cache so the field is populated before registration.
        config.getAdditionalSet();

        config.registerDynamicStorageItem(TEST_ITEM_ID, "minecraft:apple");

        assertTrue(config.getAdditionalSet().contains(TEST_ITEM_ID),
                "additionalSet must be invalidated by registerDynamicStorageItem");
    }

    @Test
    void derivedSetReflectsDerivedDynamicFoodRegistration() {
        SpoilageConfig config = SpoilageConfig.getInstance();

        // Prime the lazy derivedSet cache so the field is populated before registration.
        config.getDerivedSet();

        config.registerDynamicFoodItem(TEST_ITEM_ID, 24000L, 24000L, false, true);

        assertTrue(config.getDerivedSet().contains(TEST_ITEM_ID),
                "derivedSet must be invalidated by registerDynamicFoodItem(derived=true)");
    }
}
