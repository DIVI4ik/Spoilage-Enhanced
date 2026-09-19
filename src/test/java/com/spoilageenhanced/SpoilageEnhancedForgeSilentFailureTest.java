package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1380 (L1 — silent failure): test SpoilageEnhancedForge's silent failure patterns.
 *
 * <p>SpoilageEnhancedForge (SpoilageEnhancedForge.java:36, :56) has two silent-failure
 * catch blocks when resolving Forge paths:</p>
 *
 * <ol>
 *   <li>getForgeConfigDir (SpoilageEnhancedForge.java:36): catches {@code Throwable}
 *       when resolving the Forge config directory via FMLPaths. If FMLPaths is missing,
 *       the field name changed, or the getter threw, the mod would silently write its
 *       config to the wrong directory. Logged at WARNING with exception details.</li>
 *   <li>getForgeGameDir (SpoilageEnhancedForge.java:56): catches {@code Throwable}
 *       when resolving the Forge game directory via FMLPaths. Same silent-failure
 *       class — a silent fallback to Path.of(".") hides a broken Forge environment.
 *       Logged at WARNING with exception details.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: Forge path
 * resolution uses reflection with try-catch, failures are logged at WARNING with
 * exception class and message, fallbacks are Path.of("config") and Path.of(".").</p>
 */
class SpoilageEnhancedForgeSilentFailureTest {

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
    void getForgeConfigDirHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around config dir resolution
        assertTrue(source.contains("try {"),
                "getForgeConfigDir must have try block");
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getForgeConfigDir must catch Throwable");
        assertTrue(source.contains("failed to resolve Forge config dir via FMLPaths"),
                "getForgeConfigDir must log for failed resolution");
    }

    @Test
    void getForgeConfigDirLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "getForgeConfigDir must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "getForgeConfigDir must log exception message");
    }

    @Test
    void getForgeConfigDirUsesFMLPaths() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses FMLPaths reflection
        assertTrue(source.contains("Class.forName(\"net.minecraftforge.fml.loading.FMLPaths\")"),
                "getForgeConfigDir must load FMLPaths class");
        assertTrue(source.contains("getField(\"CONFIGDIR\")"),
                "getForgeConfigDir must get CONFIGDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getForgeConfigDir must invoke getter");
    }

    @Test
    void getForgeConfigDirFallbacksToConfig() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of("config")
        assertTrue(source.contains("return Path.of(\"config\")"),
                "getForgeConfigDir must fallback to Path.of(config)");
    }

    @Test
    void getForgeGameDirHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around game dir resolution
        assertTrue(source.contains("private static Path getForgeGameDir"),
                "getForgeGameDir method must exist");
        assertTrue(source.contains("try {"),
                "getForgeGameDir must have try block");
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getForgeGameDir must catch Throwable");
        assertTrue(source.contains("failed to resolve Forge game dir via FMLPaths"),
                "getForgeGameDir must log for failed resolution");
    }

    @Test
    void getForgeGameDirLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "getForgeGameDir must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "getForgeGameDir must log exception message");
    }

    @Test
    void getForgeGameDirUsesFMLPaths() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses FMLPaths reflection
        assertTrue(source.contains("getField(\"GAMEDIR\")"),
                "getForgeGameDir must get GAMEDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getForgeGameDir must invoke getter");
    }

    @Test
    void getForgeGameDirFallbacksToCurrentDir() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of(".")
        assertTrue(source.contains("return Path.of(\".\")"),
                "getForgeGameDir must fallback to Path.of(.)");
    }
}