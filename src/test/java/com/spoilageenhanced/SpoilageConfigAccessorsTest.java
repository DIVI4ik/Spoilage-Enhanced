package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 259 regression test: SpoilageConfig.getLootRandomizationConfig + getSpoilageSpeedMultiplier.
 *
 * <p>getLootRandomizationConfig returns the loot randomization config (never null — it
 * lazily creates one if missing). getSpoilageSpeedMultiplier returns the current speed
 * multiplier (default 1.0, negative values clamped to 0.01).</p>
 */
public class SpoilageConfigAccessorsTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lootRandomizationConfigIsNonNull() {
        SpoilageConfig.LootRandomizationConfig cfg =
                SpoilageConfig.getInstance().getLootRandomizationConfig();
        assertNotNull(cfg, "getLootRandomizationConfig must never return null");
    }

    @Test
    void lootRandomizationConfigHasDefaultValues() {
        SpoilageConfig.LootRandomizationConfig cfg =
                SpoilageConfig.getInstance().getLootRandomizationConfig();
        assertEquals(0.60f, cfg.fresh_chance, 0.001f, "Default fresh_chance must be 0.60");
        assertEquals(0.30f, cfg.stale_chance, 0.001f, "Default stale_chance must be 0.30");
        assertEquals(0.10f, cfg.rotten_chance, 0.001f, "Default rotten_chance must be 0.10");
    }

    @Test
    void speedMultiplierDefaultsToOne() {
        double multiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        assertTrue(multiplier > 0.0, "Speed multiplier must be positive, got " + multiplier);
    }

    @Test
    void speedMultiplierSetGetRoundTrip() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(2.5);
        assertEquals(2.5, config.getSpoilageSpeedMultiplier(), 0.0,
                "setSpoilageSpeedMultiplier must round-trip");
        // Restore
        config.setSpoilageSpeedMultiplier(before);
    }

    @Test
    void speedMultiplierNegativeClamped() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(-5.0);
        assertEquals(0.01, config.getSpoilageSpeedMultiplier(), 0.0,
                "Negative speed multiplier must clamp to 0.01");
        // Restore
        config.setSpoilageSpeedMultiplier(before);
    }
}
