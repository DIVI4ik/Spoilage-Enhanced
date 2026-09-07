package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 300 regression test: SpoilageConfig.save.
 *
 * <p>save() writes the config to the config file. It creates parent directories if
 * needed. This test pins the contract: save does not throw, the file exists after save.</p>
 */
public class SpoilageConfigSaveTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void saveDoesNotThrow() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertDoesNotThrow(() -> config.save(),
                "save must not throw");
    }

    @Test
    void saveProducesFile() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.save();
        java.io.File file = com.spoilageenhanced.platform.SpoilageEnhancedPlatform.getConfigDir()
                .resolve("spoilage_enhanced.json").toFile();
        assertTrue(file.exists(), "Config file must exist after save: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "Config file must not be empty");
    }
}
