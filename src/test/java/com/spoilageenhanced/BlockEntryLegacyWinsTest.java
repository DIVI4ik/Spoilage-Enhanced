package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 338 regression test: BlockSpoilageEntry serialization when BOTH legacyBirthTime
 * and state are present.
 *
 * <p>toCompound serializes an entry as legacy (BirthTime only) when legacyBirthTime >= 0,
 * ignoring the state/expirationTime fields. fromCompound reconstructs a legacy entry
 * with state=FRESH, expirationTime=-1. This test pins the contract: legacy wins.</p>
 */
public class BlockEntryLegacyWinsTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyBirthTimeWinsOverStateInSerialization() {
        // Build an entry with BOTH legacyBirthTime >= 0 AND state set.
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(5, 64, 5);
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.ROTTEN, 12345L);
        entry.legacyBirthTime = 1000L; // both fields now set

        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("entries");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, BlockSpoilageData.BlockSpoilageEntry> entries =
                    (java.util.Map<Long, BlockSpoilageData.BlockSpoilageEntry>) f.get(data);
            entries.put(pos.asLong(), entry);
        } catch (Exception e) {
            fail("Failed to inject entry: " + e.getMessage());
        }

        CompoundTag nbt = data.toCompound();
        // Verify the serialized form contains BirthTime, NOT State/Expire.
        CompoundTag blocks = nbt.getCompound("Blocks").orElseThrow();
        CompoundTag entryNbt = blocks.getCompound(String.valueOf(pos.asLong())).orElseThrow();
        assertTrue(entryNbt.contains("BirthTime"), "Legacy entry must serialize BirthTime");
        assertFalse(entryNbt.contains("State"), "Legacy entry must NOT serialize State");
        assertFalse(entryNbt.contains("Expire"), "Legacy entry must NOT serialize Expire");
        assertEquals(1000L, entryNbt.getLong("BirthTime").orElseThrow());
    }

    @Test
    void stateEntrySerializesStateAndExpire() {
        // An entry with legacyBirthTime < 0 (the default) serializes State + Expire.
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(6, 64, 6);
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.STALE, 8000L);

        CompoundTag nbt = data.toCompound();
        CompoundTag blocks = nbt.getCompound("Blocks").orElseThrow();
        CompoundTag entryNbt = blocks.getCompound(String.valueOf(pos.asLong())).orElseThrow();
        assertTrue(entryNbt.contains("State"), "State entry must serialize State");
        assertTrue(entryNbt.contains("Expire"), "State entry must serialize Expire");
        assertFalse(entryNbt.contains("BirthTime"), "State entry must NOT serialize BirthTime");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE.ordinal(), entryNbt.getInt("State").orElseThrow());
        assertEquals(8000L, entryNbt.getLong("Expire").orElseThrow());
    }
}
