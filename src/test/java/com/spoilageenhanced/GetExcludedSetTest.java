package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 324 regression test: SpoilageConfig.getExcludedSet.
 *
 * <p>getExcludedSet returns the set of excluded item IDs (items that are never spoilable).
 * It lazily creates a CHM-backed set from the excluded_items list. This test pins the
 * contract: non-null, contains known excluded items.</p>
 */
public class GetExcludedSetTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void excludedSetIsNonNull() {
        assertNotNull(SpoilageConfig.getInstance().getExcludedSet(),
                "getExcludedSet must return a non-null set");
    }

    @Test
    void excludedSetIsConsistent() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Calling twice must return the same set (cached).
        assertSame(config.getExcludedSet(), config.getExcludedSet(),
                "getExcludedSet must return the same instance on repeat calls");
    }

    @Test
    void excludedSetContainsKnownExclusions() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // The default config excludes some items. Verify the set is non-empty.
        assertFalse(config.getExcludedSet().isEmpty(),
                "The excluded set must not be empty (default config has exclusions)");
    }
}
