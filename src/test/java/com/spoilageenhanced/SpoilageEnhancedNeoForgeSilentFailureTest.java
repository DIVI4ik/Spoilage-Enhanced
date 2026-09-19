package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1381 (L1 — silent failure): test SpoilageEnhancedNeoForge's silent failure patterns.
 *
 * <p>SpoilageEnhancedNeoForge (SpoilageEnhancedNeoForge.java:36, :48) has two silent-failure
 * catch blocks when resolving NeoForge paths:</p>
 *
 * <ol>
 *   <li>getNeoForgeConfigDir (SpoilageEnhancedNeoForge.java:36): catches {@code Throwable}
 *       and IGNORES it completely — no logging at all. If FMLPaths is missing, the field
 *       name changed, or the getter threw, the mod silently writes its config to the
 *       wrong directory. This is a BUG — the Forge equivalent logs at WARNING.</li>
 *   <li>getNeoForgeGameDir (SpoilageEnhancedNeoForge.java:48): catches {@code Throwable}
 *       and IGNORES it completely — same bug. Silent fallback to Path.of(".") hides a
 *       broken NeoForge environment.</li>
 * </ol>
 *
 * <p>What this test pins is the CURRENT (buggy) behavior: failures are silently ignored.
 * This test should be updated when the code is fixed to match the Forge version's
 * logging behavior.</p>
 */
class SpoilageEnhancedNeoForgeSilentFailureTest {

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
    void getNeoForgeConfigDirHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around config dir resolution
        assertTrue(source.contains("try {"),
                "getNeoForgeConfigDir must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "getNeoForgeConfigDir must catch Throwable");
    }

    @Test
    void getNeoForgeConfigDirIgnoresException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it IGNORES the exception (BUG - should log like Forge version)
        // The catch body is empty: no SpoilageEnhancedLogger.log call inside it.
        int catchStart = source.indexOf("} catch (Throwable ignored) {");
        int catchEnd = source.indexOf("}", catchStart + 1);
        String catchBody = source.substring(catchStart, catchEnd + 1);
        assertTrue(catchBody.contains("} catch (Throwable ignored) {"),
                "getNeoForgeConfigDir must catch Throwable");
        assertFalse(catchBody.contains("SpoilageEnhancedLogger.log"),
                "getNeoForgeConfigDir currently ignores exception with empty body - BUG");
    }

    @Test
    void getNeoForgeConfigDirUsesFMLPaths() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses FMLPaths reflection
        assertTrue(source.contains("Class.forName(\"net.neoforged.fml.loading.FMLPaths\")"),
                "getNeoForgeConfigDir must load FMLPaths class");
        assertTrue(source.contains("getField(\"CONFIGDIR\")"),
                "getNeoForgeConfigDir must get CONFIGDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getNeoForgeConfigDir must invoke getter");
    }

    @Test
    void getNeoForgeConfigDirFallbacksToConfig() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of("config")
        assertTrue(source.contains("return Path.of(\"config\")"),
                "getNeoForgeConfigDir must fallback to Path.of(config)");
    }

    @Test
    void getNeoForgeGameDirHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around game dir resolution
        assertTrue(source.contains("private static Path getNeoForgeGameDir"),
                "getNeoForgeGameDir method must exist");
        assertTrue(source.contains("try {"),
                "getNeoForgeGameDir must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "getNeoForgeGameDir must catch Throwable");
    }

    @Test
    void getNeoForgeGameDirIgnoresException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it IGNORES the exception (BUG - should log like Forge version)
        int catchStart = source.indexOf("} catch (Throwable ignored) {",
                source.indexOf("getNeoForgeGameDir"));
        int catchEnd = source.indexOf("}", catchStart + 1);
        String catchBody = source.substring(catchStart, catchEnd + 1);
        assertTrue(catchBody.contains("} catch (Throwable ignored) {"),
                "getNeoForgeGameDir must catch Throwable");
        assertFalse(catchBody.contains("SpoilageEnhancedLogger.log"),
                "getNeoForgeGameDir currently ignores exception with empty body - BUG");
    }

    @Test
    void getNeoForgeGameDirUsesFMLPaths() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses FMLPaths reflection
        assertTrue(source.contains("getField(\"GAMEDIR\")"),
                "getNeoForgeGameDir must get GAMEDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getNeoForgeGameDir must invoke getter");
    }

    @Test
    void getNeoForgeGameDirFallbacksToCurrentDir() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of(".")
        assertTrue(source.contains("return Path.of(\".\")"),
                "getNeoForgeGameDir must fallback to Path.of(.)");
    }
}