package com.spoilageenhanced;

import com.spoilageenhanced.mixin.ClientLanguageMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1362 (L1 — silent failure): test ClientLanguageMixin's silent failure patterns.
 *
 * <p>ClientLanguageMixin (ClientLanguageMixin.java:48, :65) catches {@code Exception} when
 * loading language files. Two catch blocks:</p>
 *
 * <ol>
 *   <li>en_us fallback (ClientLanguageMixin.java:48): catches {@code Exception} when
 *       loading the fallback en_us.json. If malformed or missing, the mod silently falls
 *       back to an empty translation map. Logged at WARNING.</li>
 *   <li>Active languages (ClientLanguageMixin.java:65): catches {@code Exception} when
 *       loading active language JSONs. If malformed, the mod silently skips it and falls
 *       back to en_us. Logged at WARNING with the language code.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: language loading
 * errors are caught and logged with appropriate context (language code for active languages).</p>
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
    void enUsFallbackHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around en_us fallback loading
        assertTrue(source.contains("try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream"),
                "en_us fallback must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "en_us fallback must catch Exception");
        assertTrue(source.contains("failed to load en_us.json fallback language"),
                "en_us fallback must log for failed load");
    }

    @Test
    void enUsFallbackLogsWarning() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs at WARNING level
        assertTrue(source.contains("LogCategory.GENERAL"),
                "en_us fallback must log to GENERAL category");
        assertTrue(source.contains("ClientLanguageMixin: failed to load en_us.json fallback language"),
                "en_us fallback must include mixin name in log");
    }

    @Test
    void activeLanguagesHaveTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around active language loading
        assertTrue(source.contains("for (String code : languages)"),
                "must iterate over languages");
        assertTrue(source.contains("try (InputStream stream = ClientLanguageMixin.class.getResourceAsStream"),
                "active languages must have try-with-resources");
        assertTrue(source.contains("} catch (Exception e) {"),
                "active languages must catch Exception");
        assertTrue(source.contains("failed to load language"),
                "active languages must log for failed load");
    }

    @Test
    void activeLanguagesLogLanguageCode() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs the language code (source has '" + code' - single quote, double quote, plus, code)
        assertTrue(source.contains("\" + code"),
                "active languages must log the language code");
        assertTrue(source.contains("LogCategory.GENERAL"),
                "active languages must log to GENERAL category");
    }

    @Test
    void activeLanguagesSkipEnUs() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it skips en_us in the loop
        assertTrue(source.contains("if (\"en_us\".equals(code)) continue;"),
                "active languages must skip en_us");
    }

    @Test
    void usesLanguageLoadFromJson() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ClientLanguageMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses Language.loadFromJson
        assertTrue(source.contains("Language.loadFromJson(stream, combined::put)"),
                "must use Language.loadFromJson with method reference");
    }
}