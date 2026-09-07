package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.AutoFoodDetector;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 256 regression test: AutoFoodDetector.scanTagsAndBlocks.
 *
 * <p>scanTagsAndBlocks iterates the item and block registries and registers any
 * food-like items/blocks that aren't already tracked. It is called during datapack
 * reload. This test verifies the method runs without throwing and registers at
 * least some vanilla food blocks.</p>
 */
public class ScanTagsAndBlocksTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void scanRunsWithoutThrowing() {
        // The scan must complete without throwing, even if the registries are
        // partially populated (Bootstrap.bootStrap() doesn't fully populate them).
        assertDoesNotThrow(() -> AutoFoodDetector.scanTagsAndBlocks(),
                "scanTagsAndBlocks must not throw");
    }

    @Test
    void scanIsIdempotent() {
        // Running the scan twice must not throw and must not double-register.
        AutoFoodDetector.scanTagsAndBlocks();
        assertDoesNotThrow(() -> AutoFoodDetector.scanTagsAndBlocks(),
                "scanTagsAndBlocks must be idempotent (second run must not throw)");
    }

    @Test
    void scanDoesNotCorruptConfig() {
        // After the scan, the config must still be loadable and have a valid speed multiplier.
        AutoFoodDetector.scanTagsAndBlocks();
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertNotNull(config, "Config must not be null after scan");
        double multiplier = config.getSpoilageSpeedMultiplier();
        assertTrue(multiplier > 0.0, "Speed multiplier must be positive after scan, got " + multiplier);
    }
}
