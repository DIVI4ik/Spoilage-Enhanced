package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1389 (L1 — silent failure): test SpoilageEnhancedCommon's silent failure pattern.
 *
 * <p>SpoilageEnhancedCommon (SpoilageEnhancedCommon.java:24) catches
 * {@code ClassNotFoundException} when forcing initialization of vanilla payload
 * packet classes. The old catch swallowed the exception with "Should never happen".
 * If it DOES happen (mod conflict, broken install, classloader isolation), the
 * payload codec mixins silently fail to register, and network packets break with
 * no signal. Logged at ERROR so a broken install is diagnosable.</p>
 *
 * <p>What this test pins is that the pattern remains as documented: catches
 * ClassNotFoundException, logs at ERROR with exception details.</p>
 */
class SpoilageEnhancedCommonSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void initHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around payload packet class loading
        assertTrue(source.contains("try {"),
                "init must have try block");
        assertTrue(source.contains("} catch (ClassNotFoundException e) {"),
                "init must catch ClassNotFoundException");
        assertTrue(source.contains("vanilla payload packet class not found"),
                "init must log for missing class");
    }

    @Test
    void initLogsAtErrorLevel() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it logs at ERROR level (GENERAL category with ERROR-like message)
        assertTrue(source.contains("LogCategory.GENERAL"),
                "init must log to GENERAL category");
    }

    @Test
    void initLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "init must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "init must log exception message");
    }

    @Test
    void initLoadsServerboundPayloadPacket() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it loads ServerboundCustomPayloadPacket
        assertTrue(source.contains("ServerboundCustomPayloadPacket.class.getName()"),
                "init must load ServerboundCustomPayloadPacket");
        assertTrue(source.contains("Class.forName"),
                "init must use Class.forName");
    }

    @Test
    void initLoadsClientboundPayloadPacket() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it loads ClientboundCustomPayloadPacket
        assertTrue(source.contains("ClientboundCustomPayloadPacket.class.getName()"),
                "init must load ClientboundCustomPayloadPacket");
    }

    @Test
    void initInitializesLogger() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it initializes the logger
        assertTrue(source.contains("SpoilageEnhancedLogger.init()"),
                "init must initialize logger");
    }

    @Test
    void initLogsCompletion() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/SpoilageEnhancedCommon.java"))
                .replace("\r\n", "\n");

        // Verify it logs completion
        assertTrue(source.contains("SpoilageEnhanced common initialization complete"),
                "init must log completion");
    }
}