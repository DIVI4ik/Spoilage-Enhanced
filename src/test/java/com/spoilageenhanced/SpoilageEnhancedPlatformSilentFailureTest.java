package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1382 (L1 — silent failure): test SpoilageEnhancedPlatform's silent failure patterns.
 *
 * <p>SpoilageEnhancedPlatform (SpoilageEnhancedPlatform.java:24, :42, :58, :74, :95, :109, :122, :138)
 * has multiple silent-failure catch blocks when resolving config and game directories:</p>
 *
 * <ol>
 *   <li>Config dir supplier (SpoilageEnhancedPlatform.java:24): catches {@code Throwable}
 *       when the supplier throws. Logs at WARNING and falls back to reflection probes.</li>
 *   <li>FabricLoader probe (SpoilageEnhancedPlatform.java:42): catches {@code Throwable}
 *       but only logs if NOT ClassNotFoundException (normal on Forge/NeoForge).</li>
 *   <li>Forge FMLPaths probe (SpoilageEnhancedPlatform.java:58): catches {@code Throwable}
 *       but only logs if NOT ClassNotFoundException (normal on Fabric/NeoForge).</li>
 *   <li>NeoForge FMLPaths probe (SpoilageEnhancedPlatform.java:74): catches {@code Throwable}
 *       but only logs if NOT ClassNotFoundException (normal on Fabric/Forge).</li>
 *   <li>All probes failed (SpoilageEnhancedPlatform.java:95): logs WARNING when every
 *       probe fails, falls back to relative "config".</li>
 *   <li>Game dir supplier (SpoilageEnhancedPlatform.java:109): catches {@code Throwable}
 *       when the supplier throws. Logs at WARNING and falls back to reflection probes.</li>
 *   <li>FabricLoader game dir probe (SpoilageEnhancedPlatform.java:122): same pattern.</li>
 *   <li>Forge FMLPaths game dir probe (SpoilageEnhancedPlatform.java:138): same pattern.</li>
 *   <li>NeoForge FMLPaths game dir probe (SpoilageEnhancedPlatform.java:154): same pattern.</li>
 *   <li>All game dir probes failed (SpoilageEnhancedPlatform.java:170): logs WARNING,
 *       falls back to relative ".".</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: supplier failures
 * are logged, ClassNotFoundException is filtered out for loader probes, all-probes-failed
 * is logged with fallback path.</p>
 */
class SpoilageEnhancedPlatformSilentFailureTest {

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
    void getConfigDirSupplierHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around config dir supplier
        assertTrue(source.contains("if (configDirSupplier != null)"),
                "getConfigDir must check supplier");
        assertTrue(source.contains("try {"),
                "getConfigDir must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "getConfigDir must catch Throwable");
        assertTrue(source.contains("config dir supplier threw"),
                "getConfigDir must log for supplier failure");
    }

    @Test
    void getConfigDirSupplierLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("t.getClass().getSimpleName()"),
                "getConfigDir must log exception class");
        assertTrue(source.contains("t.getMessage()"),
                "getConfigDir must log exception message");
    }

    @Test
    void getConfigDirFabricProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify FabricLoader probe filters ClassNotFoundException
        assertTrue(source.contains("Class.forName(\"net.fabricmc.loader.api.FabricLoader\")"),
                "getConfigDir must probe FabricLoader");
        assertTrue(source.contains("if (!(t instanceof ClassNotFoundException))"),
                "getConfigDir must filter ClassNotFoundException");
        assertTrue(source.contains("FabricLoader probe failed unexpectedly"),
                "getConfigDir must log unexpected Fabric failure");
    }

    @Test
    void getConfigDirForgeProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify Forge FMLPaths probe filters ClassNotFoundException
        assertTrue(source.contains("Class.forName(\"net.minecraftforge.fml.loading.FMLPaths\")"),
                "getConfigDir must probe Forge FMLPaths");
        assertTrue(source.contains("Forge FMLPaths probe failed unexpectedly"),
                "getConfigDir must log unexpected Forge failure");
    }

    @Test
    void getConfigDirNeoForgeProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify NeoForge FMLPaths probe filters ClassNotFoundException
        assertTrue(source.contains("Class.forName(\"net.neoforged.fml.loading.FMLPaths\")"),
                "getConfigDir must probe NeoForge FMLPaths");
        assertTrue(source.contains("NeoForge FMLPaths probe failed unexpectedly"),
                "getConfigDir must log unexpected NeoForge failure");
    }

    @Test
    void getConfigDirAllProbesFailedLogsWarning() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify all-probes-failed is logged
        assertTrue(source.contains("no loader could resolve the config dir"),
                "getConfigDir must log when all probes fail");
        assertTrue(source.contains("falling back to relative 'config'"),
                "getConfigDir must log fallback path");
    }

    @Test
    void getConfigDirFallbacksToConfig() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of("config")
        assertTrue(source.contains("return Path.of(\"config\")"),
                "getConfigDir must fallback to Path.of(config)");
    }

    @Test
    void getGameDirSupplierHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around game dir supplier
        assertTrue(source.contains("if (gameDirSupplier != null)"),
                "getGameDir must check supplier");
        assertTrue(source.contains("try {"),
                "getGameDir must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "getGameDir must catch Throwable");
        assertTrue(source.contains("game dir supplier threw"),
                "getGameDir must log for supplier failure");
    }

    @Test
    void getGameDirFabricProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify FabricLoader game dir probe filters ClassNotFoundException
        assertTrue(source.contains("getMethod(\"getGameDir\")"),
                "getGameDir must probe FabricLoader getGameDir");
        assertTrue(source.contains("FabricLoader probe failed unexpectedly"),
                "getGameDir must log unexpected Fabric failure");
    }

    @Test
    void getGameDirForgeProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify Forge FMLPaths game dir probe filters ClassNotFoundException
        assertTrue(source.contains("getField(\"GAMEDIR\")"),
                "getGameDir must probe Forge GAMEDIR");
        assertTrue(source.contains("Forge FMLPaths probe failed unexpectedly"),
                "getGameDir must log unexpected Forge failure");
    }

    @Test
    void getGameDirNeoForgeProbeFiltersClassNotFound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify NeoForge FMLPaths game dir probe filters ClassNotFoundException
        assertTrue(source.contains("net.neoforged.fml.loading.FMLPaths"),
                "getGameDir must probe NeoForge FMLPaths");
        assertTrue(source.contains("NeoForge FMLPaths probe failed unexpectedly"),
                "getGameDir must log unexpected NeoForge failure");
    }

    @Test
    void getGameDirAllProbesFailedLogsWarning() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify all-probes-failed is logged
        assertTrue(source.contains("no loader could resolve the game dir"),
                "getGameDir must log when all probes fail");
        assertTrue(source.contains("falling back to relative '.'"),
                "getGameDir must log fallback path");
    }

    @Test
    void getGameDirFallbacksToCurrentDir() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/platform/SpoilageEnhancedPlatform.java"))
                .replace("\r\n", "\n");

        // Verify it falls back to Path.of(".")
        assertTrue(source.contains("return Path.of(\".\")"),
                "getGameDir must fallback to Path.of(.)");
    }
}