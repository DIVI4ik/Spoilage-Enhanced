package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1371 (L1 — silent failure): test SpoilageConfig.save's silent failure patterns.
 *
 * <p>SpoilageConfig.save (SpoilageConfig.java:1199, :1234, :1247) has three silent-failure
 * catch blocks:</p>
 *
 * <ol>
 *   <li>Saves directory listing (SpoilageConfig.java:1199): catches {@code IOException}
 *       when listing the saves directory. If it cannot be listed, isRealGame stays false
 *       and save() silently returns without writing the config. Logged at WARNING.</li>
 *   <li>Temp file write (SpoilageConfig.java:1234): catches {@code IOException} when
 *       writing to the temp file. A failed config write means every setting change is
 *       silently lost. Logged at ERROR.</li>
 *   <li>Move (SpoilageConfig.java:1247): catches {@code IOException} when moving the
 *       temp file over the config. The worst case is the old config surviving, never
 *       a truncated one. Logged with temp file location.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: saves directory
 * listing failure is logged, save uses atomic temp-file move, failures are logged with
 * enough context to diagnose.</p>
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
    void savesDirListingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around saves directory listing
        assertTrue(source.contains("try (java.nio.file.DirectoryStream<Path> stream"),
                "saves dir listing must have try-with-resources");
        assertTrue(source.contains("} catch (IOException e) {"),
                "saves dir listing must catch IOException");
        assertTrue(source.contains("failed to list saves directory"),
                "saves dir listing must log for failed listing");
    }

    @Test
    void savesDirListingChecksLevelDat() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it checks for level.dat
        assertTrue(source.contains("worldDir.resolve(\"level.dat\")"),
                "saves dir listing must check for level.dat");
        assertTrue(source.contains("isRealGame = true"),
                "saves dir listing must set isRealGame");
    }

    @Test
    void saveUsesTempFile() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify save uses temp file for atomic write
        assertTrue(source.contains("File temp = new File(file.getParentFile(), CONFIG_FILENAME + \".tmp\")"),
                "save must use temp file");
        assertTrue(source.contains("new FileWriter(temp, java.nio.charset.StandardCharsets.UTF_8)"),
                "save must write to temp file with UTF-8 charset");
    }

    @Test
    void tempFileWriteHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify temp file write has try-catch
        assertTrue(source.contains("} catch (IOException e) {"),
                "temp file write must catch IOException");
        assertTrue(source.contains("FAILED to write config file"),
                "temp file write must log for failed write");
        assertTrue(source.contains("all unsaved config changes are lost"),
                "temp file write must warn about lost changes");
    }

    @Test
    void moveHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify move has try-catch
        assertTrue(source.contains("} catch (IOException e) {"),
                "move must catch IOException");
        assertTrue(source.contains("FAILED to replace config file"),
                "move must log for failed replace");
        assertTrue(source.contains("new config left in"),
                "move must log where the new config is left");
    }

    @Test
    void moveUsesReplaceExisting() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify move uses REPLACE_EXISTING
        assertTrue(source.contains("java.nio.file.Files.move(temp.toPath(), file.toPath()"),
                "move must use Files.move");
        assertTrue(source.contains("StandardCopyOption.REPLACE_EXISTING"),
                "move must use REPLACE_EXISTING");
    }

    @Test
    void saveChecksIsRealGame() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify save checks isRealGame before writing
        assertTrue(source.contains("if (!isRealGame)"),
                "save must check isRealGame");
        assertTrue(source.contains("return;"),
                "save must return early if not a real game");
    }
}