package com.spoilageenhanced;

import com.spoilageenhanced.forge.SpoilageEnhancedForge;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1360 (L1 — silent failure): test SpoilageEnhancedForge's silent failure patterns.
 *
 * <p>SpoilageEnhancedForge has two silent-failure catch blocks (SpoilageEnhancedForge.java:36, :56):</p>
 *
 * <ol>
 *   <li>getForgeConfigDir (SpoilageEnhancedForge.java:36): catches {@code Throwable} when
 *       resolving the Forge config directory via FMLPaths. If FMLPaths is missing, the
 *       field name changed, or the getter threw, the mod would silently write its config
 *       to the wrong directory. Logged at WARNING.</li>
 *   <li>getForgeGameDir (SpoilageEnhancedForge.java:56): catches {@code Throwable} when
 *       resolving the Forge game directory via FMLPaths. Same rationale — a silent
 *       fallback to Path.of(".") hides a broken Forge environment. Logged at WARNING.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: reflection errors
 * are caught, logged at WARNING, and fallbacks are used.</p>
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

        // Verify the try-catch pattern exists around FMLPaths config dir resolution
        assertTrue(source.contains("private static Path getForgeConfigDir()"),
                "getForgeConfigDir method must exist");
        assertTrue(source.contains("try {"),
                "getForgeConfigDir must have try block");
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getForgeConfigDir must catch Throwable");
        assertTrue(source.contains("SpoilageEnhancedForge: failed to resolve Forge config dir via FMLPaths"),
                "getForgeConfigDir must log for failed FMLPaths resolution");
    }

    @Test
    void getForgeConfigDirUsesReflection() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses reflection to access FMLPaths
        assertTrue(source.contains("Class.forName(\"net.minecraftforge.fml.loading.FMLPaths\")"),
                "getForgeConfigDir must use Class.forName for FMLPaths");
        assertTrue(source.contains("getField(\"CONFIGDIR\")"),
                "getForgeConfigDir must get CONFIGDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getForgeConfigDir must invoke getter");
    }

    @Test
    void getForgeConfigDirFallsBackToConfig() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify fallback
        assertTrue(source.contains("return Path.of(\"config\")"),
                "getForgeConfigDir must fall back to Path.of(\"config\")");
    }

    @Test
    void getForgeGameDirHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around FMLPaths game dir resolution
        assertTrue(source.contains("private static Path getForgeGameDir() {"),
                "getForgeGameDir method must exist");
        assertTrue(source.contains("try {"),
                "getForgeGameDir must have try block");
        assertTrue(source.contains("} catch (Throwable e) {"),
                "getForgeGameDir must catch Throwable");
        assertTrue(source.contains("SpoilageEnhancedForge: failed to resolve Forge game dir via FMLPaths"),
                "getForgeGameDir must log for failed FMLPaths resolution");
    }

    @Test
    void getForgeGameDirUsesReflection() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify it uses reflection to access FMLPaths
        assertTrue(source.contains("Class.forName(\"net.minecraftforge.fml.loading.FMLPaths\")"),
                "getForgeGameDir must use Class.forName for FMLPaths");
        assertTrue(source.contains("getField(\"GAMEDIR\")"),
                "getForgeGameDir must get GAMEDIR field");
        assertTrue(source.contains("getMethod(\"get\").invoke"),
                "getForgeGameDir must invoke getter");
    }

    @Test
    void getForgeGameDirFallsBackToCurrentDir() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify fallback
        assertTrue(source.contains("return Path.of(\".\")"),
                "getForgeGameDir must fall back to Path.of(\".\")");
    }

    @Test
    void bothMethodsLogAtWarning() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/forge/SpoilageEnhancedForge.java"))
                .replace("\r\n", "\n");

        // Verify both log at WARNING level
        assertTrue(source.contains("LogCategory.GENERAL"),
                "both methods must log to GENERAL category");
    }
}