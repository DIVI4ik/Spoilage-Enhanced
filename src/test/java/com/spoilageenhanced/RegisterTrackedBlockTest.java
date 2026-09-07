package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 266 regression test: SpoilageConfig.registerTrackedBlock.
 *
 * <p>registerTrackedBlock adds a block-to-drop mapping to the tracked_blocks map.
 * It returns true if the mapping was added, false if it already existed or if
 * either argument is null. This test pins the contract.</p>
 */
public class RegisterTrackedBlockTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registerNewBlockReturnsTrue() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        String testBlockId = "testmod:test_block_" + System.nanoTime();
        boolean added = config.registerTrackedBlock(testBlockId, "minecraft:apple", false);
        assertTrue(added, "Registering a new block must return true");
        assertEquals("minecraft:apple", config.getTrackedBlockDropItem(testBlockId),
                "The registered block must resolve to the drop item");
    }

    @Test
    void registerExistingBlockReturnsFalse() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        String testBlockId = "testmod:test_block_dup_" + System.nanoTime();
        config.registerTrackedBlock(testBlockId, "minecraft:apple", false);
        boolean secondAdd = config.registerTrackedBlock(testBlockId, "minecraft:carrot", false);
        assertFalse(secondAdd, "Registering an existing block must return false");
        assertEquals("minecraft:apple", config.getTrackedBlockDropItem(testBlockId),
                "The first registration must win (putIfAbsent)");
    }

    @Test
    void registerNullBlockReturnsFalse() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertFalse(config.registerTrackedBlock(null, "minecraft:apple", false),
                "null blockId must return false");
    }

    @Test
    void registerNullDropReturnsFalse() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        assertFalse(config.registerTrackedBlock("testmod:test", null, false),
                "null dropItemId must return false");
    }
}
