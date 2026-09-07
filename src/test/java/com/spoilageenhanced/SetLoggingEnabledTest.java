package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 352 regression test: SpoilageEnhancedLogger.setLoggingEnabled.
 *
 * <p>setLoggingEnabled updates the config flag, saves the config, refreshes the static
 * flag cache, and calls init() or closeWriters() based on the new value. This test
 * pins the contract: the flag round-trips, the cache stays in sync, no exception.</p>
 */
public class SetLoggingEnabledTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void setLoggingEnabledTrueRoundTrips() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        boolean before = config.enable_logging;
        try {
            assertDoesNotThrow(() -> SpoilageEnhancedLogger.setLoggingEnabled(true),
                    "setLoggingEnabled(true) must not throw");
            assertTrue(config.enable_logging,
                    "enable_logging must be true after setLoggingEnabled(true)");
        } finally {
            SpoilageEnhancedLogger.setLoggingEnabled(before);
        }
    }

    @Test
    void setLoggingEnabledFalseRoundTrips() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        boolean before = config.enable_logging;
        try {
            assertDoesNotThrow(() -> SpoilageEnhancedLogger.setLoggingEnabled(false),
                    "setLoggingEnabled(false) must not throw");
            assertFalse(config.enable_logging,
                    "enable_logging must be false after setLoggingEnabled(false)");
        } finally {
            SpoilageEnhancedLogger.setLoggingEnabled(before);
        }
    }

    @Test
    void isTraceEnabledFollowsLoggingFlag() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        boolean loggingBefore = config.enable_logging;
        boolean traceBefore = config.enableTraceLogging;
        try {
            config.enableTraceLogging = true;
            SpoilageEnhancedLogger.setLoggingEnabled(false);
            assertFalse(SpoilageEnhancedLogger.isTraceEnabled(),
                    "isTraceEnabled must be false when enable_logging is false, even if trace is on");
        } finally {
            config.enableTraceLogging = traceBefore;
            SpoilageEnhancedLogger.setLoggingEnabled(loggingBefore);
        }
    }
}
