package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 322 regression test: SpoilageEnhancedLogger.LogCategory enum.
 *
 * <p>LogCategory has 7 values, each with a filename. The filenames are used to create
 * the log files in init(). This test pins the contract: all 7 values exist, each has
 * a non-null filename ending in .log, all filenames are distinct.</p>
 */
public class LogCategoryTest {

    @Test
    void allSevenCategoriesExist() {
        assertEquals(7, SpoilageEnhancedLogger.LogCategory.values().length,
                "LogCategory must have exactly 7 values");
        Set<SpoilageEnhancedLogger.LogCategory> expected = EnumSet.of(
                SpoilageEnhancedLogger.LogCategory.GENERAL,
                SpoilageEnhancedLogger.LogCategory.HUD,
                SpoilageEnhancedLogger.LogCategory.DATA,
                SpoilageEnhancedLogger.LogCategory.EVENTS,
                SpoilageEnhancedLogger.LogCategory.CHUNKS,
                SpoilageEnhancedLogger.LogCategory.NETWORK,
                SpoilageEnhancedLogger.LogCategory.TRACE);
        assertEquals(expected, EnumSet.allOf(SpoilageEnhancedLogger.LogCategory.class),
                "All 7 categories must be present");
    }

    @Test
    void eachCategoryHasFilename() {
        for (SpoilageEnhancedLogger.LogCategory cat : SpoilageEnhancedLogger.LogCategory.values()) {
            assertNotNull(cat.filename, cat + " must have a filename");
            assertTrue(cat.filename.endsWith(".log"),
                    cat + " filename must end in .log, got: " + cat.filename);
        }
    }

    @Test
    void filenamesAreDistinct() {
        Set<String> names = new java.util.HashSet<>();
        for (SpoilageEnhancedLogger.LogCategory cat : SpoilageEnhancedLogger.LogCategory.values()) {
            assertTrue(names.add(cat.filename),
                    "Filename must be distinct, duplicate: " + cat.filename);
        }
    }

    @Test
    void valueOfRoundTrips() {
        for (SpoilageEnhancedLogger.LogCategory cat : SpoilageEnhancedLogger.LogCategory.values()) {
            assertEquals(cat, SpoilageEnhancedLogger.LogCategory.valueOf(cat.name()),
                    cat + " must round-trip through valueOf");
        }
    }
}
