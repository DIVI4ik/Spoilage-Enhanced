package com.spoilageenhanced;

import com.spoilageenhanced.mixin.LanguageMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1363 (L1 — silent failure): test LanguageMixin's silent failure pattern.
 *
 * <p>LanguageMixin (LanguageMixin.java:22) catches {@code Exception} when loading
 * the spoilage_enhanced en_us language file during vanilla's parseTranslations.
 * If the stream fails to read, it logs the failure.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists and logs appropriately.</p>
 */
class LanguageMixinSilentFailureTest {

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
    void onParseTranslationsHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/LanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around language loading
        assertTrue(source.contains("try (InputStream stream = LanguageMixin.class.getResourceAsStream"),
                "onParseTranslations must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "onParseTranslations must catch Exception");
        assertTrue(source.contains("failed to load spoilage_enhanced en_us lang"),
                "onParseTranslations must log for failed load");
    }

    @Test
    void onParseTranslationsChecksPath() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/LanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it only runs for en_us.json
        assertTrue(source.contains("\"/assets/minecraft/lang/en_us.json\".equals(path)"),
                "onParseTranslations must check for en_us.json path");
    }

    @Test
    void onParseTranslationsUsesLanguageLoadFromJson() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/LanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses Language.loadFromJson
        assertTrue(source.contains("Language.loadFromJson(stream, output)"),
                "onParseTranslations must use Language.loadFromJson");
    }

    @Test
    void onParseTranslationsLogsMixinName() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/LanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs with mixin name
        assertTrue(source.contains("LanguageMixin: failed to load spoilage_enhanced en_us lang"),
                "onParseTranslations must include mixin name in log");
    }
}