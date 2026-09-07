package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpoilageConfigCacheTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testCacheClearInvalidation() {
        SpoilageConfig config = SpoilageConfig.getInstance();

        // Baseline check
        assertTrue(config.isSpoilable(Items.APPLE), "Apple must be spoilable");

        // Clear cache should keep it functional and refresh properly
        config.clearCache();
        assertTrue(config.isSpoilable(Items.APPLE), "Apple must remain spoilable after cache clear");
    }

    @Test
    void clearDurationCacheDoesNotThrow() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Populate the duration cache
        config.getBaseFreshDurationForItem(Items.APPLE);
        config.getBaseStaleDurationForItem(Items.APPLE);
        // Clear it
        assertDoesNotThrow(() -> SpoilageConfig.clearDurationCache(),
                "clearDurationCache must not throw");
        // Cache must still work after clear
        assertTrue(config.getBaseFreshDurationForItem(Items.APPLE) > 0,
                "Duration must be retrievable after clear");
    }

    @Test
    void reloadDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageConfig.reload(),
                "reload must not throw");
    }
}
