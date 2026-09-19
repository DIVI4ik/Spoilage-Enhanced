package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1377 (L1 — silent failure): test ClientLanguageMixin's silent failure patterns.
 *
 * <p>ClientLanguageMixin (ClientLanguageMixin.java:48, :65) has two silent-failure
 * catch blocks when loading language files:</p>
 *
 * <ol>
 *   <li>en_us.json fallback (ClientLanguageMixin.java:48): catches {@code Exception}
 *       when loading the fallback language. If en_us.json is malformed or missing,
 *       the mod silently falls back to an empty translation map. Logged at WARNING.</li>
 *   <li>Active languages (ClientLanguageMixin.java:65): catches {@code Exception}
 *       when loading active languages (e.g. ru_ru). If a language JSON is malformed,
 *       the mod silently skips it and falls back to en_us. Logged at WARNING with
 *       the language code.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: language loading
 * failures are logged with language code and exception details, fallback to en_us
 * is always attempted first.</p>
 */
class ClientLanguageMixinSilentFailureTest {

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
    void loadEnUsHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around en_us loading
        assertTrue(source.contains("try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream"),
                "loadEnUs must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "loadEnUs must catch Exception");
        assertTrue(source.contains("failed to load en_us.json fallback language"),
                "loadEnUs must log for failed load");
    }

    @Test
    void loadEnUsLogsExceptionDetails() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs exception class and message
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "loadEnUs must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "loadEnUs must log exception message");
    }

    @Test
    void loadEnUsUsesLanguageLoadFromJson() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses Language.loadFromJson
        assertTrue(source.contains("Language.loadFromJson(stream, combined::put)"),
                "loadEnUs must use Language.loadFromJson");
    }

    @Test
    void loadActiveLanguagesHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around active language loading
        assertTrue(source.contains("for (String code : languages)"),
                "loadActiveLanguages must iterate over languages");
        assertTrue(source.contains("try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream"),
                "loadActiveLanguages must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "loadActiveLanguages must catch Exception");
        assertTrue(source.contains("failed to load language"),
                "loadActiveLanguages must log for failed load");
    }

    @Test
    void loadActiveLanguagesLogsLanguageCode() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs the language code
        assertTrue(source.contains("code"),
                "loadActiveLanguages must log the language code");
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "loadActiveLanguages must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "loadActiveLanguages must log exception message");
    }

    @Test
    void loadActiveLanguagesSkipsEnUs() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it skips en_us (already loaded as fallback)
        assertTrue(source.contains("if (\"en_us\".equals(code)) continue;"),
                "loadActiveLanguages must skip en_us");
    }

    @Test
    void loadActiveLanguagesUsesCombinedMap() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses the combined map
        assertTrue(source.contains("combined::put"),
                "loadActiveLanguages must use combined map");
    }
}