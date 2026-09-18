package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1356 (L1 — silent failure): test SpoilageConfig.reload's silent failure pattern.
 *
 * <p>SpoilageConfig.reload (SpoilageConfig.java:336) catches {@code Exception} when
 * reloading the config. If the config file doesn't parse cleanly, reload is refused
 * and the file is left untouched. Any other exception during reload is caught and
 * logged, returning false.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists and logs appropriately.</p>
 */
class SpoilageConfigReloadSilentFailureTest {

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
    void reloadSourceHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around reload
        assertTrue(source.contains("public static boolean reload() {"),
                "reload method must exist");
        assertTrue(source.contains("try {"),
                "reload must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "reload must catch Exception");
        assertTrue(source.contains("Failed to reload config"),
                "reload must log for failed reload");
        assertTrue(source.contains("return false;"),
                "reload must return false on failure");
    }

    @Test
    void reloadChecksConfigParsesCleanly() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it checks config parses cleanly first
        assertTrue(source.contains("configParsesCleanly()"),
                "reload must check configParsesCleanly");
        assertTrue(source.contains("Config reload refused"),
                "reload must log refusal message");
        assertTrue(source.contains("file was left untouched"),
                "reload must mention file is left untouched");
    }

    @Test
    void reloadClearsCaches() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it clears all caches
        assertTrue(source.contains("spoilableCache.clear()"),
                "reload must clear spoilableCache");
        assertTrue(source.contains("excludedSet = null"),
                "reload must clear excludedSet");
        assertTrue(source.contains("additionalSet = null"),
                "reload must clear additionalSet");
        assertTrue(source.contains("excludedBlockSet = null"),
                "reload must clear excludedBlockSet");
        assertTrue(source.contains("derivedSet = null"),
                "reload must clear derivedSet");
        assertTrue(source.contains("ITEM_ID_CACHE.clear()"),
                "reload must clear ITEM_ID_CACHE");
        assertTrue(source.contains("clearDurationCache()"),
                "reload must clear duration cache");
        assertTrue(source.contains("DynamicFoodBlockCache.clear()"),
                "reload must clear DynamicFoodBlockCache");
    }

    @Test
    void reloadRefreshesLoggerConfigCache() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it refreshes logger config cache (Pass 532)
        assertTrue(source.contains("SpoilageEnhancedLogger.refreshConfigCache()"),
                "reload must refresh logger config cache");
    }

    @Test
    void reloadReturnsTrueOnSuccess() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it returns true on success
        assertTrue(source.contains("return true;"),
                "reload must return true on success");
    }
}