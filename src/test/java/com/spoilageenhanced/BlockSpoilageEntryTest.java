package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 284 regression test: BlockSpoilageData.BlockSpoilageEntry.
 *
 * <p>BlockSpoilageEntry is the data record for a tracked block. It has three constructors:
 * no-arg (defaults), state+expiration, and legacyBirthTime. This test pins the constructor
 * contracts.</p>
 */
public class BlockSpoilageEntryTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noArgConstructorUsesDefaults() {
        BlockSpoilageData.BlockSpoilageEntry entry = new BlockSpoilageData.BlockSpoilageEntry();
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, entry.state,
                "Default state must be FRESH");
        assertEquals(-1L, entry.expirationTime, "Default expirationTime must be -1");
        assertEquals(-1L, entry.legacyBirthTime, "Default legacyBirthTime must be -1");
    }

    @Test
    void stateExpirationConstructorSetsFields() {
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.STALE, 5000L);
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, entry.state,
                "State must be set");
        assertEquals(5000L, entry.expirationTime, "ExpirationTime must be set");
        assertEquals(-1L, entry.legacyBirthTime, "LegacyBirthTime must remain default");
    }

    @Test
    void legacyBirthTimeConstructorSetsField() {
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(1000L);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, entry.state,
                "State must remain default (FRESH)");
        assertEquals(-1L, entry.expirationTime, "ExpirationTime must remain default");
        assertEquals(1000L, entry.legacyBirthTime, "LegacyBirthTime must be set");
    }
}
