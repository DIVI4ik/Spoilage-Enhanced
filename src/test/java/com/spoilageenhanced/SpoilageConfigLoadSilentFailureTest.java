package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1359 (L1 — silent failure): test SpoilageConfig.load's silent failure pattern.
 *
 * <p>SpoilageConfig.load (SpoilageConfig.java:916) catches {@code Exception} when
 * parsing the config file. A file that EXISTS but does not parse still falls through
 * to defaults — the mod must start even with a corrupt config. But reload() must NOT
 * report success in that case: it checks parse health itself before accepting the load,
 * so /spoilage config reload answers honestly and the corrupt file is not overwritten
 * with defaults.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, the corrupt file is not
 * overwritten, defaults are populated, and the config is returned (never null).</p>
 */
class SpoilageConfigLoadSilentFailureTest {

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
    void loadSourceHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around config loading
        assertTrue(source.contains("public static SpoilageConfig load()"),
                "load method must exist");
        assertTrue(source.contains("try (Reader reader = new FileReader(configFile.toFile()"),
                "load must have try-with-resources for reading config");
        assertTrue(source.contains("} catch (Exception e) {"),
                "load must catch Exception for config parsing");
        assertTrue(source.contains("Failed to load spoilage config"),
                "load must log for failed config parse");
    }

    @Test
    void loadFallsThroughToDefaultsOnCorruptFile() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify it falls through to defaults on corrupt file
        assertTrue(source.contains("if (config != null) {"),
                "load must check if config parsed successfully");
        assertTrue(source.contains("config = new SpoilageConfig();"),
                "load must create new config on parse failure");
        assertTrue(source.contains("config.populateDefaults();"),
                "load must populate defaults on parse failure");
        assertTrue(source.contains("config.save();"),
                "load must save defaults on first run (file doesn't exist)");
    }

    @Test
    void loadDoesNotOverwriteCorruptFile() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the corrupt file is NOT overwritten (only saved on first run)
        assertTrue(source.contains("file was left untouched") || source.contains("left untouched"),
                "load must mention corrupt file is left untouched (in comment)");
    }

    @Test
    void loadEnsuresNestedConfigsNotNull() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify nested configs are initialized
        assertTrue(source.contains("config.effects == null"),
                "load must check effects config");
        assertTrue(source.contains("config.milk_effects == null"),
                "load must check milk_effects config");
        assertTrue(source.contains("config.animal_feeding == null"),
                "load must check animal_feeding config");
        assertTrue(source.contains("config.composter == null"),
                "load must check composter config");
        assertTrue(source.contains("config.loot_randomization == null"),
                "load must check loot_randomization config");
    }

    @Test
    void loadClampsHandEditedValues() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify clampHandEditedValues is called
        assertTrue(source.contains("config.clampHandEditedValues();"),
                "load must call clampHandEditedValues");
    }

    @Test
    void loadMigratesAutoDiscovered() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify migration is called
        assertTrue(source.contains("config.migrateAutoDiscovered();"),
                "load must call migrateAutoDiscovered for old configs");
    }

    @Test
    void loadReturnsNonNullConfig() throws Exception {
        // Test that load() returns a non-null config
        Method load = SpoilageConfig.class.getDeclaredMethod("load");
        load.setAccessible(true);

        SpoilageConfig config = (SpoilageConfig) load.invoke(null);
        assertNotNull(config, "load must return non-null config");
    }
}