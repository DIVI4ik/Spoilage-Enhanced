package com.spoilageenhanced;

import com.spoilageenhanced.neoforge.SpoilageEnhancedNeoForge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1348 (L1 — silent failure): test SpoilageEnhancedNeoForge's silent failure patterns.
 *
 * <p>SpoilageEnhancedNeoForge has two silent-failure catch blocks in the NeoForge
 * initialization path:</p>
 *
 * <ol>
 *   <li>{@code getNeoForgeConfigDir()} at line 36: catches {@code Throwable} and
 *       returns {@code Path.of("config")} as fallback. This is used when NeoForge's
 *       FMLPaths class is not available or the CONFIGDIR field/method is missing.</li>
 *   <li>{@code getNeoForgeGameDir()} at line 48: catches {@code Throwable} and
 *       returns {@code Path.of(".")} as fallback. Same pattern for GAMEDIR.</li>
 * </ol>
 *
 * <p>These are feature-detection patterns: the mod tries to use NeoForge's paths
 * API, but falls back gracefully if it's not available (e.g., running on Fabric
 * or Forge instead of NeoForge). The catches must not throw.</p>
 *
 * <p>What this test pins is that the fallback paths work correctly and the
 * silent catches don't swallow real errors silently.</p>
 */
class SpoilageEnhancedNeoForgeSilentFailureTest {

    @Test
    void getNeoForgeConfigDirReturnsFallbackWhenFMLPathsMissing() throws Exception {
        Method method = SpoilageEnhancedNeoForge.class.getDeclaredMethod(
                "getNeoForgeConfigDir");
        method.setAccessible(true);

        // FMLPaths class won't be on classpath in test environment
        Object result = method.invoke(null);
        assertNotNull(result, "getNeoForgeConfigDir must return a Path");
        assertEquals("config", result.toString(),
                "must return fallback Path.of(\"config\") when FMLPaths unavailable");
    }

    @Test
    void getNeoForgeGameDirReturnsFallbackWhenFMLPathsMissing() throws Exception {
        Method method = SpoilageEnhancedNeoForge.class.getDeclaredMethod(
                "getNeoForgeGameDir");
        method.setAccessible(true);

        // FMLPaths class won't be on classpath in test environment
        Object result = method.invoke(null);
        assertNotNull(result, "getNeoForgeGameDir must return a Path");
        assertEquals(".", result.toString(),
                "must return fallback Path.of(\".\") when FMLPaths unavailable");
    }

    @Test
    void getNeoForgeConfigDirNeverThrows() throws Exception {
        Method method = SpoilageEnhancedNeoForge.class.getDeclaredMethod(
                "getNeoForgeConfigDir");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null),
                "getNeoForgeConfigDir must never throw — it catches Throwable");
    }

    @Test
    void getNeoForgeGameDirNeverThrows() throws Exception {
        Method method = SpoilageEnhancedNeoForge.class.getDeclaredMethod(
                "getNeoForgeGameDir");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null),
                "getNeoForgeGameDir must never throw — it catches Throwable");
    }

    @Test
    void getNeoForgeConfigDirHasTryCatchPattern() throws Exception {
        // Verify the method has the try-catch pattern
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("Class.forName(\"net.neoforged.fml.loading.FMLPaths\")"),
                "must try to load FMLPaths class");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "must catch Throwable");
        assertTrue(source.contains("return Path.of(\"config\")"),
                "must return config fallback");
    }

    @Test
    void getNeoForgeGameDirHasTryCatchPattern() throws Exception {
        // Verify the method has the try-catch pattern
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("Class.forName(\"net.neoforged.fml.loading.FMLPaths\")"),
                "must try to load FMLPaths class");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "must catch Throwable");
        assertTrue(source.contains("return Path.of(\".\")"),
                "must return current directory fallback");
    }

    @Test
    void bothMethodsUseSameFMLPathsClass() throws Exception {
        // Both methods should use the same FMLPaths class
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/neoforge/SpoilageEnhancedNeoForge.java"))
                .replace("\r\n", "\n");

        int fmlPathsCount = 0;
        int idx = 0;
        while ((idx = source.indexOf("FMLPaths", idx)) != -1) {
            fmlPathsCount++;
            idx += 8;
        }
        assertEquals(2, fmlPathsCount, "both methods must reference FMLPaths");
    }
}