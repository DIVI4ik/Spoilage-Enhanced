package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 299 regression test: SpoilageConfig.setSpoilageSpeedMultiplier.
 *
 * <p>setSpoilageSpeedMultiplier sets the speed multiplier and saves the config. It
 * clamps non-positive values to 0.01. This test pins the contract.</p>
 */
public class SetSpoilageSpeedMultiplierTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void setGetRoundTrip() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(3.0);
        assertEquals(3.0, config.getSpoilageSpeedMultiplier(), 0.0,
                "setSpoilageSpeedMultiplier must round-trip");
        config.setSpoilageSpeedMultiplier(before);
    }

    @Test
    void negativeValueClampedToMinimum() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(-5.0);
        assertEquals(0.01, config.getSpoilageSpeedMultiplier(), 0.0,
                "Negative multiplier must clamp to 0.01");
        config.setSpoilageSpeedMultiplier(before);
    }

    @Test
    void zeroValueClampedToMinimum() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(0.0);
        assertEquals(0.01, config.getSpoilageSpeedMultiplier(), 0.0,
                "Zero multiplier must clamp to 0.01");
        config.setSpoilageSpeedMultiplier(before);
    }

    @Test
    void valueAbove100ClampedToMaximum() {
        // Pass 631 (Lens 7): the config load path clamps to [0.01, 100] but the runtime
        // setter previously had no upper clamp. Calling setSpoilageSpeedMultiplier(1000.0)
        // would leave the field at 1000, which then feeds rescaleItemTimestamps as a ratio
        // of up to 1000/1 = 1000. (long)(remaining * 1000) can overflow for large
        // remaining values.
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(1000.0);
        assertEquals(100.0, config.getSpoilageSpeedMultiplier(), 0.0,
                "Multiplier above 100 must clamp to 100 (matches config load clamping)");
        config.setSpoilageSpeedMultiplier(before);
    }

    @Test
    void nanValueClampedToDefault() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double before = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(Double.NaN);
        assertEquals(0.01, config.getSpoilageSpeedMultiplier(), 0.0,
                "NaN multiplier must clamp to 0.01 (NaN <= 0.0 is false, so without the NaN check it would pass through)");
        config.setSpoilageSpeedMultiplier(before);
    }
}
