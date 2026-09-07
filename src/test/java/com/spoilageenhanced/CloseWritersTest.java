package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 319 regression test: SpoilageEnhancedLogger.closeWriters.
 *
 * <p>closeWriters stops the writer thread, flushes and closes all writers, clears the
 * writers map, and sets initialized=false. It is idempotent — calling it twice must
 * not throw. This test pins the contract.</p>
 */
public class CloseWritersTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void closeWritersDoesNotThrow() {
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.closeWriters(),
                "closeWriters must not throw");
    }

    @Test
    void closeWritersIsIdempotent() {
        SpoilageEnhancedLogger.closeWriters();
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.closeWriters(),
                "closeWriters must be idempotent (second call must not throw)");
    }

    @Test
    void closeWritersAfterInitDoesNotThrow() {
        SpoilageEnhancedLogger.init();
        assertDoesNotThrow(() -> SpoilageEnhancedLogger.closeWriters(),
                "closeWriters after init must not throw");
    }

    @Test
    void closeFailuresCounterIsAccessible() throws Exception {
        // Pass 626 (Lens 1): the closeFailures field is package-private internally; verify
        // it can be read via reflection so tests can assert the counter increments on
        // forced-shutdown scenarios. The field is volatile so a reader always sees the
        // latest write.
        java.lang.reflect.Field f = SpoilageEnhancedLogger.class.getDeclaredField("closeFailures");
        f.setAccessible(true);
        int before = f.getInt(null);
        assertTrue(before >= 0, "closeFailures must be a non-negative int");
    }
}
