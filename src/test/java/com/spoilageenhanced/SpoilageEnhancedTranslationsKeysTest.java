package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 346 regression test: SpoilageEnhancedTranslations translation-key constants.
 *
 * <p>SpoilageEnhancedTranslations defines 12 tooltip keys + 5 command keys = 17
 * public static final String constants. This test pins the contract: all exist,
 * are non-null, non-empty, and distinct.</p>
 */
public class SpoilageEnhancedTranslationsKeysTest {

    @Test
    void allTooltipKeysExistAndAreNonNull() {
        String[] tooltipKeys = {
                "TOOLTIP_STACK_DETAILS",
                "TOOLTIP_FRESH_COUNT",
                "TOOLTIP_STALE_COUNT",
                "TOOLTIP_ROTTEN_COUNT",
                "TOOLTIP_FRESH",
                "TOOLTIP_ROTTEN_WITH_COUNT",
                "TOOLTIP_STALE_WITH_COUNT",
                "TOOLTIP_SHIFT_DETAILS",
                "TOOLTIP_SPOILS_IN",
                "TOOLTIP_NEXT_SPOIL"
        };
        for (String key : tooltipKeys) {
            try {
                Field f = SpoilageEnhancedTranslations.class.getDeclaredField(key);
                String value = (String) f.get(null);
                assertNotNull(value, key + " must be non-null");
                assertFalse(value.isEmpty(), key + " must be non-empty");
                assertTrue(value.startsWith("spoilage_enhanced.tooltip."),
                        key + " must start with spoilage_enhanced.tooltip., got: " + value);
            } catch (Exception e) {
                fail("Failed to read " + key + ": " + e.getMessage());
            }
        }
    }

    @Test
    void allCommandKeysExistAndAreNonNull() {
        String[] commandKeys = {
                "CMD_LOGGING_SET",
                "CMD_LOGGING_ENABLED",
                "CMD_LOGGING_DISABLED",
                "CMD_CONFIG_RELOADED",
                "CMD_CONFIG_RELOAD_FAILED"
        };
        for (String key : commandKeys) {
            try {
                Field f = SpoilageEnhancedTranslations.class.getDeclaredField(key);
                String value = (String) f.get(null);
                assertNotNull(value, key + " must be non-null");
                assertFalse(value.isEmpty(), key + " must be non-empty");
                assertTrue(value.startsWith("spoilage_enhanced.command."),
                        key + " must start with spoilage_enhanced.command., got: " + value);
            } catch (Exception e) {
                fail("Failed to read " + key + ": " + e.getMessage());
            }
        }
    }

    @Test
    void allKeysAreDistinct() {
        Set<String> values = new java.util.HashSet<>();
        for (Field f : SpoilageEnhancedTranslations.class.getDeclaredFields()) {
            if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
                try {
                    String value = (String) f.get(null);
                    assertTrue(values.add(value),
                            "Duplicate translation key value: " + value + " (field: " + f.getName() + ")");
                } catch (Exception e) {
                    fail("Failed to read " + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }
}