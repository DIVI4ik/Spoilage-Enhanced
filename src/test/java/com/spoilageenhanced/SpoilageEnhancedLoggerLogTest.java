package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 310 regression test: SpoilageEnhancedLogger.log with all categories.
 *
 * <p>log() writes to the async queue. This test pins the contract: all categories
 * are accepted, logging is best-effort (no exception thrown).</p>
 */
public class SpoilageEnhancedLoggerLogTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SpoilageEnhancedLogger.init();
    }

    @Test
    void logGeneralDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log("test general"),
                "log(GENERAL) must not throw");
    }

    @Test
    void logHudDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.HUD, "test hud"),
                "log(HUD) must not throw");
    }

    @Test
    void logDataDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA, "test data"),
                "log(DATA) must not throw");
    }

    @Test
    void logEventsDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.EVENTS, "test events"),
                "log(EVENTS) must not throw");
    }

    @Test
    void logChunksDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.CHUNKS, "test chunks"),
                "log(CHUNKS) must not throw");
    }

    @Test
    void logNetworkDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.NETWORK, "test network"),
                "log(NETWORK) must not throw");
    }

    @Test
    void logTraceDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "test trace"),
                "log(TRACE) must not throw");
    }
}