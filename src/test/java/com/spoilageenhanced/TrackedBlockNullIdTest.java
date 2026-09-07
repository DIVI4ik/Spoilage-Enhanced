package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 327 regression test: SpoilageConfig.getTrackedBlockDropItem + isBlockTracked null guard.
 *
 * <p>tracked_blocks is a ConcurrentHashMap, and CHM.containsKey(null) throws
 * NullPointerException. The old code passed blockId straight into containsKey, so a
 * null id crashed instead of returning null/false. The fix guards null before the CHM
 * lookup. This test fails against the old code (NPE) and passes with the fix.</p>
 */
public class TrackedBlockNullIdTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nullBlockIdReturnsNullNotNpe() {
        assertDoesNotThrow(() ->
                assertNull(SpoilageConfig.getInstance().getTrackedBlockDropItem(null),
                        "null blockId must return null, not throw NPE"));
    }

    @Test
    void nullBlockIdIsNotTracked() {
        assertDoesNotThrow(() ->
                assertFalse(SpoilageConfig.getInstance().isBlockTracked(null),
                        "null blockId must return false, not throw NPE"));
    }

    @Test
    void emptyBlockIdReturnsNull() {
        assertNull(SpoilageConfig.getInstance().getTrackedBlockDropItem(""),
                "empty blockId must return null (no CHM entry, no fallback match)");
        assertFalse(SpoilageConfig.getInstance().isBlockTracked(""),
                "empty blockId must not be tracked");
    }

    @Test
    void knownBlockStillResolves() {
        // The guard must not break the normal path.
        assertEquals("minecraft:pumpkin",
                SpoilageConfig.getInstance().getTrackedBlockDropItem("minecraft:pumpkin"),
                "Known block must still resolve after the null guard");
    }
}
