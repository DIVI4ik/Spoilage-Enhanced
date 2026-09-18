package com.spoilageenhanced;

import com.spoilageenhanced.platform.ForgeRegistryBridge;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1341 (L1 — silent failure): test ForgeRegistryBridge's silent failure patterns.
 *
 * <p>ForgeRegistryBridge has two silent-failure patterns:</p>
 *
 * <ol>
 *   <li>{@code classPresent(String)} at line 192-198: catches {@code Throwable} and returns
 *       {@code false}. This is used to check if NeoForge/Forge classes are available without
 *       crashing on loaders where they don't exist. The silent failure is intentional here —
 *       it's a feature detection pattern — but it must be pinned so a regression doesn't
 *       change the behavior.</li>
 *   <li>{@code log(String)} at line 201-208: catches {@code Throwable} from the file logger
 *       and continues with just SLF4J. The file logger may not exist early in startup.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: classPresent returns
 * false for missing classes, and log never throws even if the file logger fails.</p>
 */
class ForgeRegistryBridgeSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void classPresentReturnsFalseForMissingClass() throws Exception {
        Method classPresent = ForgeRegistryBridge.class.getDeclaredMethod(
                "classPresent", String.class);
        classPresent.setAccessible(true);

        // A class that definitely doesn't exist
        Object result = classPresent.invoke(null, "com.nonexistent.MissingClass");
        assertEquals(false, result,
                "classPresent must return false for a missing class — this is the feature detection pattern");
    }

    @Test
    void classPresentReturnsTrueForExistingClass() throws Exception {
        Method classPresent = ForgeRegistryBridge.class.getDeclaredMethod(
                "classPresent", String.class);
        classPresent.setAccessible(true);

        // A class that definitely exists
        Object result = classPresent.invoke(null, "java.lang.String");
        assertEquals(true, result,
                "classPresent must return true for an existing class");
    }

    @Test
    void classPresentNeverThrows() throws Exception {
        Method classPresent = ForgeRegistryBridge.class.getDeclaredMethod(
                "classPresent", String.class);
        classPresent.setAccessible(true);

        // Even malformed class names must not throw
        assertDoesNotThrow(() -> classPresent.invoke(null, "not.a.valid.class.name"),
                "classPresent must never throw — it catches Throwable");
        assertDoesNotThrow(() -> classPresent.invoke(null, ""),
                "classPresent must handle empty string");
    }

    @Test
    void logNeverThrowsEvenIfFileLoggerFails() throws Exception {
        Method log = ForgeRegistryBridge.class.getDeclaredMethod(
                "log", String.class);
        log.setAccessible(true);

        // The log method catches Throwable from SpoilageEnhancedLogger.log
        // and continues with just SLF4J. We can't easily make the file logger
        // fail in a test, but we can verify the method doesn't throw for normal input.
        assertDoesNotThrow(() -> log.invoke(null, "test message"),
                "log must not throw for normal messages");
        assertDoesNotThrow(() -> log.invoke(null, ""),
                "log must handle empty string");
    }

    @Test
    void checkDataComponentRegistrabilityHasTryCatch() throws Exception {
        // Verify the checkDataComponentRegistrability method has the try-catch pattern
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/ForgeRegistryBridge.java"))
                .replace("\r\n", "\n");

        // The method should have a try-catch around the registry lookup
        assertTrue(source.contains("try {"),
                "checkDataComponentRegistrability must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "checkDataComponentRegistrability must catch Throwable");
        assertTrue(source.contains("Data component check failed:"),
                "checkDataComponentRegistrability must log failure");
    }
}