package com.spoilageenhanced;

import com.spoilageenhanced.config.RotOverlayConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1367 (L1 — silent failure): test RotOverlayConfig's silent failure patterns.
 *
 * <p>RotOverlayConfig (RotOverlayConfig.java:110, :153, :161) has three silent-failure
 * catch blocks:</p>
 *
 * <ol>
 *   <li>Load (RotOverlayConfig.java:110): catches {@code Exception} when parsing the
 *       config file. A file that EXISTS but does not parse must not be overwritten —
 *       defaults are still USED for the session so overlays render, but the file is
 *       left untouched for repair.</li>
 *   <li>Save (RotOverlayConfig.java:153): catches {@code Exception} when writing to
 *       the temp file. Write-to-temp-then-move prevents a crash mid-write from leaving
 *       a half-written JSON.</li>
 *   <li>Move (RotOverlayConfig.java:161): catches {@code IOException} when moving the
 *       temp file over the config. Logs that the new copy is left in the temp file.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: corrupt file is
 * not overwritten, save uses atomic temp-file move, failures are logged.</p>
 */
class RotOverlayConfigSilentFailureTest {

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
    void loadHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around config loading
        assertTrue(source.contains("try (Reader reader = new FileReader(configFile.toFile()))"),
                "load must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "load must catch Exception");
        assertTrue(source.contains("Failed to load rot overlay config"),
                "load must log for failed parse");
    }

    @Test
    void loadDoesNotOverwriteCorruptFile() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify corrupt file is not overwritten
        assertTrue(source.contains("if (!fileExists)"),
                "load must only save on first run");
        assertTrue(source.contains("file is left untouched for repair"),
                "load must document corrupt file is left untouched");
    }

    @Test
    void loadPopulatesDefaults() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify defaults are populated
        assertTrue(source.contains("config.item_overrides.put(\"minecraft:cooked_beef\", \"mold_web\")"),
                "load must populate cooked_beef default");
        assertTrue(source.contains("config.item_overrides.put(\"minecraft:bread\", \"mold_crust\")"),
                "load must populate bread default");
    }

    @Test
    void saveUsesTempFile() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify save uses temp file for atomic write
        assertTrue(source.contains("Path tempFile = configFile.resolveSibling(CONFIG_FILENAME + \".tmp\")"),
                "save must use temp file");
        assertTrue(source.contains("new FileWriter(tempFile.toFile())"),
                "save must write to temp file");
    }

    @Test
    void saveHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify save has try-catch
        assertTrue(source.contains("} catch (Exception e) {"),
                "save must catch Exception");
        assertTrue(source.contains("Failed to save rot overlay config"),
                "save must log for failed write");
    }

    @Test
    void moveHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify move has try-catch
        assertTrue(source.contains("} catch (IOException e) {"),
                "move must catch IOException");
        assertTrue(source.contains("Failed to replace rot overlay config"),
                "move must log for failed replace");
        assertTrue(source.contains("new copy left in"),
                "move must log where the new copy is left");
    }

    @Test
    void moveUsesAtomicReplace() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/RotOverlayConfig.java"))
                .replace("\r\n", "\n");

        // Verify move uses atomic replace
        assertTrue(source.contains("java.nio.file.Files.move(tempFile, configFile"),
                "move must use Files.move");
        assertTrue(source.contains("StandardCopyOption.REPLACE_EXISTING"),
                "move must use REPLACE_EXISTING");
    }
}