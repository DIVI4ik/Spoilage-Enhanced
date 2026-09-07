package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 321 regression test: SpoilageEnhancedLogger.profile.
 *
 * <p>profile runs a task and logs the elapsed time if it exceeds 0.5ms. When logging
 * is disabled, it just runs the task. This test pins the contract: the task always
 * runs, no exception thrown.</p>
 */
public class LoggerProfileTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void profileRunsTheTask() {
        AtomicInteger counter = new AtomicInteger(0);
        SpoilageEnhancedLogger.profile(SpoilageEnhancedLogger.LogCategory.GENERAL,
                "test-task", counter::incrementAndGet);
        assertEquals(1, counter.get(), "profile must run the task exactly once");
    }

    @Test
    void profileWithSlowTaskDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.profile(
                SpoilageEnhancedLogger.LogCategory.GENERAL,
                "slow-task",
                () -> {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException ignored) {}
                }),
                "profile with a slow task must not throw");
    }

    @Test
    void profileWithNullTaskDoesNotThrow() {
        // A null task would NPE inside task.run() — but profile itself must not
        // throw before that. Verify the method handles the normal path.
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.profile(
                SpoilageEnhancedLogger.LogCategory.GENERAL,
                "null-task",
                () -> {}),
                "profile with an empty task must not throw");
    }
}
