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
 * <p>SpoilageEnhancedNeoForge (SpoilageEnhancedNeoForge.java:36, :48) had two silent-failure
 * catch blocks when resolving NeoForge paths:</p>
 *
 * <ol>
 *   <li>getNeoForgeConfigDir: caught {@code Throwable} and IGNORED it completely — no logging
 *       at all. If FMLPaths is missing, the field name changed, or the getter threw, the mod
 *       silently wrote its config to the wrong directory. This was a BUG — the Forge equivalent
 *       logs at WARNING.</li>
 *   <li>getNeoForgeGameDir: caught {@code Throwable} and IGNORED it completely — same bug.
 *       Silent fallback to Path.of(".") hid a broken NeoForge environment.</li>
 * </ol>
 *
 * <p>Pass 1414 (L1 — silent failure): fixed to match Forge version's logging behavior.
 * Both methods now log at WARNING with the exception class and message before falling back.</p>
 *
 * <p>What this test pins: the FIXED behavior — failures are logged, not silently ignored.</p>
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
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getNeoForgeConfigDir must catch Throwable as 'e'");
    }

    @Test
    void getNeoForgeConfigDirLogsException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it LOGS the exception (FIXED - matches Forge version)
        int catchStart = source.indexOf("} catch (Throwable e) {");
        int catchEnd = source.indexOf("}", catchStart + 1);
        String catchBody = source.substring(catchStart, catchEnd + 1);
        assertTrue(catchBody.contains("SpoilageEnhancedLogger.log"),
                "getNeoForgeConfigDir must log exception");
        assertTrue(catchBody.contains("SpoilageEnhancedLogger.LogCategory.GENERAL"),
                "getNeoForgeConfigDir must log at GENERAL category");
        assertTrue(catchBody.contains("e.getClass().getSimpleName()"),
                "getNeoForgeConfigDir must log exception class");
        assertTrue(catchBody.contains("e.getMessage()"),
                "getNeoForgeConfigDir must log exception message");
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
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getNeoForgeGameDir must catch Throwable as 'e'");
    }

    @Test
    void getNeoForgeGameDirLogsException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        // Verify it LOGS the exception (FIXED - matches Forge version)
        int catchStart = source.indexOf("} catch (Throwable e) {",
                source.indexOf("getNeoForgeGameDir"));
        int catchEnd = source.indexOf("}", catchStart + 1);
        String catchBody = source.substring(catchStart, catchEnd + 1);
        assertTrue(catchBody.contains("SpoilageEnhancedLogger.log"),
                "getNeoForgeGameDir must log exception");
        assertTrue(catchBody.contains("SpoilageEnhancedLogger.LogCategory.GENERAL"),
                "getNeoForgeGameDir must log at GENERAL category");
        assertTrue(catchBody.contains("e.getClass().getSimpleName()"),
                "getNeoForgeGameDir must log exception class");
        assertTrue(catchBody.contains("e.getMessage()"),
                "getNeoForgeGameDir must log exception message");
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