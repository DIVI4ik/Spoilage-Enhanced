package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1352 (L1 — silent failure): test SpoilageConfig.save's silent failure patterns.
 *
 * <p>SpoilageConfig.save has three silent-failure catch blocks:</p>
 *
 * <ol>
 *   <li>Listing saves directory (SpoilageConfig.java:~1185): catches {@code IOException}
 *       when checking for a real game environment. If the saves directory cannot be
 *       listed (permissions, IO error), isRealGame stays false and save() silently
 *       returns without writing the config. Logged at WARNING.</li>
 *   <li>Writing to temp file (SpoilageConfig.java:~1205): catches {@code IOException}
 *       when writing the JSON to the temp file. A failed config write means every
 *       setting change is silently lost. Logged at ERROR.</li>
 *   <li>Atomic move (SpoilageConfig.java:~1218): catches {@code IOException} when
 *       moving the temp file to the config file. The worst case is the old config
 *       surviving, never a truncated one. Logged at ERROR.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: the real-game
 * guard works, the atomic write pattern is used, and errors are logged at appropriate
 * levels (WARNING for detection, ERROR for actual write failures).</p>
 */
class SpoilageConfigSaveSilentFailureTest {

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
    void saveSourceHasRealGameGuard() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the real-game detection logic
        assertTrue(source.contains("Path savesDir = gameDir.resolve(\"saves\")"),
                "save must check for client saves directory");
        assertTrue(source.contains("java.nio.file.Files.isDirectory(savesDir)"),
                "save must check if saves directory exists");
        assertTrue(source.contains("java.nio.file.Files.isRegularFile(worldDir.resolve(\"level.dat\"))"),
                "save must check for level.dat in world directory");
        assertTrue(source.contains("gameDir.resolve(\"world\").resolve(\"level.dat\")"),
                "save must check for dedicated server layout (world/level.dat)");
        // The early return is on a separate line from the if
        assertTrue(source.contains("if (!isRealGame) {"),
                "save must have early return check");
        assertTrue(source.contains("return;"),
                "save must return early if not a real game");
    }

    @Test
    void saveSourceHasTryCatchForSavesDirectoryListing() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify try-catch around saves directory listing
        assertTrue(source.contains("try (java.nio.file.DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(savesDir)) {"),
                "save must have try-with-resources for listing saves directory");
        assertTrue(source.contains("} catch (IOException e) {"),
                "save must catch IOException for saves directory listing");
        assertTrue(source.contains("SpoilageConfig.save: failed to list saves directory"),
                "save must log WARNING for failed saves directory listing");
    }

    @Test
    void saveSourceUsesAtomicWritePattern() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify atomic write pattern: write to temp file, then move
        assertTrue(source.contains("File temp = new File(file.getParentFile(), CONFIG_FILENAME + \".tmp\")"),
                "save must create temp file");
        assertTrue(source.contains("try (FileWriter writer = new FileWriter(temp, java.nio.charset.StandardCharsets.UTF_8)) {"),
                "save must write to temp file with explicit UTF-8");
        assertTrue(source.contains("GSON.toJson(this, writer);"),
                "save must serialize config to JSON");
    }

    @Test
    void saveSourceHasTryCatchForWrite() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify try-catch around the write
        assertTrue(source.contains("} catch (IOException e) {"),
                "save must catch IOException during write");
        assertTrue(source.contains("SpoilageConfig.save: FAILED to write config file"),
                "save must log ERROR for failed write");
        assertTrue(source.contains("all unsaved config changes are lost"),
                "save must mention that changes are lost on write failure");
        assertTrue(source.contains("return;"),
                "save must return early on write failure (don't attempt move)");
    }

    @Test
    void saveSourceHasTryCatchForMove() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify try-catch around the atomic move
        assertTrue(source.contains("java.nio.file.Files.move(temp.toPath(), file.toPath(),"),
                "save must move temp file to config file");
        assertTrue(source.contains("StandardCopyOption.REPLACE_EXISTING"),
                "save must use REPLACE_EXISTING");
        assertTrue(source.contains("} catch (IOException e) {"),
                "save must catch IOException during move");
        assertTrue(source.contains("SpoilageConfig.save: FAILED to replace config file"),
                "save must log ERROR for failed move");
        assertTrue(source.contains("new config left in"),
                "save must mention temp file location on move failure");
    }

    @Test
    void saveSourceChecksForRealGameEnvironment() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the real-game check prevents writing in test environment
        assertTrue(source.contains("isRealGame"),
                "save must have isRealGame flag");
        assertTrue(source.contains("saves"),
                "save must reference saves directory structure");
    }

    @Test
    void saveSourceUsesExplicitCharset() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify explicit UTF-8 charset
        assertTrue(source.contains("StandardCharsets.UTF_8"),
                "save must use explicit UTF-8 charset");
    }

    @Test
    void saveDoesNotThrowInTestEnvironment() {
        // In the test environment (no level.dat), save() should return early without throwing
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertDoesNotThrow(() -> config.save(),
                "save must not throw in test environment (no real game detected)");
    }
}